package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.dao.RegulationDao;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.beauty.vo.RagChunkVO;
import com.sklabs1037.sklabs1037.beauty.vo.RagDocVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.client.VoyageClient;
import com.sklabs1037.sklabs1037.ops.service.JobRunService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 화장품 규제 Q&A (RAG: 검색 증강 생성)
 *
 * 문서 올리기 (관리자)
 *   PDF·TXT → 쪽별 글자 추출(PDFBox) → 문단 단위로 쪼개기(약 700자, 앞 문단 끝 120자 겹침, "제○조" 앞에서 끊기)
 *   → Voyage로 벡터(1024) → T_RAG_CHUNK (pgvector) → READY
 *   - 별도 스레드에서 처리 (큰 문서는 몇 분), 화면에서 진행 상황(문단 수) 확인
 *
 * 질문 (로그인 회원)
 *   질문 → 벡터 → 코사인 거리가 가까운 문단 6개 → Claude에게 "이 발췌만 근거로, 문장마다 [번호]"
 *   → 답 + 출처(문서·쪽·발췌·유사도)
 *   - 발췌에 없는 내용은 "등록된 문서에서 찾지 못했어요" (지어내지 않게)
 *   - 유사도가 너무 낮으면 Claude를 부르지 않고 바로 "못 찾음" (비용·환각 방지)
 */
@Slf4j
@Service
public class RegulationQaService {

    // 쪼개기 기준 (글자 수)
    private static final int CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 120;
    private static final int MAX_CHUNKS = 3000;

    // 검색
    private static final int TOP_K = 6;
    private static final double MIN_SCORE = 0.25;

    private static final Pattern ARTICLE = Pattern.compile("(?=제\\s?\\d+\\s?조(의\\d+)?\\s?[(（])");
    private static final Pattern CITE = Pattern.compile("\\[(\\d+)]");

    private static final String ANSWER_PROMPT = """
            너는 화장품 규제 문서 도우미야. 사용자의 질문에 아래 [발췌]만 근거로 답해.
            규칙:
            - 발췌에 있는 내용만 써. 발췌에 없으면 "등록된 문서에서 찾지 못했어요"라고 말하고 짐작하지 마.
            - 근거가 된 문장 끝에 발췌 번호를 [1], [2]처럼 붙여.
            - 조문 번호·수치(%, 기한 등)는 발췌에 적힌 그대로 옮겨.
            - 한국어로 간결하게. 짧은 문단과 "-" 목록만 쓰고, 표와 제목(#)은 쓰지 마.
            - 마지막 줄에 "※ 법률 자문이 아니며, 최신 고시·법령 원문을 확인하세요."를 붙여.
            """;

    @Autowired
    private RegulationDao regulationDao;

    @Autowired
    private BeautyLabDao beautyLabDao;

    @Autowired
    private VoyageClient voyageClient;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private JobRunService jobRunService;

    @Autowired
    private JsonMapper jsonMapper;

    // 한 번에 임베딩할 문단 토큰 수 (무료 한도에 맞춰 작게, 한글은 대략 1.5자 = 1토큰으로 어림)
    @Value("${voyage.batch-tokens:8000}")
    private int batchTokens;

    // 서버 시작: 지난번에 처리하다 멈춘 문서 → FAIL (로컬 PC에서는 운영 문서를 건드리지 않게 건너뜀)
    @EventListener(ApplicationReadyEvent.class)
    public void failLeftovers() {

        if (!jobRunService.backgroundEnabled()) return;

        try {
            int n = regulationDao.failStaleDocs();
            if (n > 0) log.info("처리가 멈춘 규제 문서 정리: {}건", n);
        } catch (Exception e) {
            log.warn("규제 문서 정리 실패: {}", e.getMessage());
        }

    }

    // ============================================
    // 문서 목록 / 삭제
    // ============================================
    public List<RagDocVO> docs() {

        return regulationDao.selectDocs();

    }

    public boolean delete(Long docId) {

        return regulationDao.deleteDoc(docId) > 0;

    }

    public boolean ready() {

        return voyageClient.isConfigured();

    }

    // ============================================
    // 문서 올리기 → 별도 스레드에서 처리
    // ============================================
    public RagDocVO upload(Long userId, String title, String source, String fileName, byte[] bytes) {

        if (!voyageClient.isConfigured()) throw new IllegalStateException("VOYAGE_API_KEY가 설정되지 않아 문서를 처리할 수 없어요.");

        // 먼저 글자를 뽑아서 확인 (스캔 PDF처럼 글자가 없으면 바로 알려줌)
        List<String> pages = extractPages(fileName, bytes);
        int letters = pages.stream().mapToInt(p -> p.replaceAll("\\s", "").length()).sum();
        if (letters < 50) throw new IllegalArgumentException("문서에서 글자를 거의 찾지 못했어요. 스캔한 이미지 PDF는 지원하지 않아요.");

        RagDocVO doc = new RagDocVO();
        doc.setTitle(title);
        doc.setSource(source);
        doc.setFileName(fileName);
        doc.setUserId(userId);
        regulationDao.insertDoc(doc);

        RagDocVO pageInfo = new RagDocVO();
        pageInfo.setDocId(doc.getDocId());
        pageInfo.setPageCount(pages.size());
        regulationDao.updateDoc(pageInfo);

        Thread worker = new Thread(() -> ingest(doc.getDocId(), title, pages), "rag-ingest-" + doc.getDocId());
        worker.setDaemon(true);
        worker.start();

        return doc;

    }

    // 쪼개기 → 임베딩 → 저장 (진행 상황은 CHUNK_COUNT로)
    private void ingest(Long docId, String title, List<String> pages) {

        try {

            List<Chunk> chunks = chunk(pages);
            if (chunks.size() > MAX_CHUNKS) chunks = chunks.subList(0, MAX_CHUNKS);

            log.info("규제 문서 처리 시작: {} ({}쪽, 문단 {}개)", title, pages.size(), chunks.size());

            int saved = 0;
            int from = 0;

            while (from < chunks.size()) {

                // 토큰 한도 안에서 묶기
                int to = from;
                int tokens = 0;
                while (to < chunks.size() && (to == from || tokens + estimateTokens(chunks.get(to).text()) <= batchTokens) && to - from < 128) {
                    tokens += estimateTokens(chunks.get(to).text());
                    to++;
                }

                List<Chunk> batch = chunks.subList(from, to);
                // 문서 이름을 앞에 붙여 벡터에 문맥을 담음 (저장은 본문만)
                List<String> inputs = batch.stream().map(c -> "[" + title + "] " + c.text()).toList();
                List<float[]> vectors = voyageClient.embed(inputs, "document");

                for (int i = 0; i < batch.size(); i++) {

                    RagChunkVO vo = new RagChunkVO();
                    vo.setDocId(docId);
                    vo.setSeq(from + i + 1);
                    vo.setPage(batch.get(i).page());
                    vo.setContent(batch.get(i).text());
                    vo.setEmbedding(VoyageClient.toPgVector(vectors.get(i)));
                    regulationDao.insertChunk(vo);
                    saved++;

                }

                RagDocVO progress = new RagDocVO();
                progress.setDocId(docId);
                progress.setChunkCount(saved);
                progress.setMessage("처리 중 " + saved + " / " + chunks.size());
                regulationDao.updateDoc(progress);

                from = to;

            }

            RagDocVO done = new RagDocVO();
            done.setDocId(docId);
            done.setStatus("READY");
            done.setChunkCount(saved);
            done.setMessage(null);
            regulationDao.updateDoc(done);

            log.info("규제 문서 처리 완료: {} (문단 {}개)", title, saved);

        } catch (Exception e) {

            log.error("규제 문서 처리 실패: {}", title, e);

            RagDocVO fail = new RagDocVO();
            fail.setDocId(docId);
            fail.setStatus("FAIL");
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            fail.setMessage(msg.length() > 400 ? msg.substring(0, 400) : msg);
            regulationDao.updateDoc(fail);

        }

    }

    // ============================================
    // 질문
    // ============================================
    public Map<String, Object> ask(Long userId, String question) {

        if (countToday(userId) >= BeautyLabService.DAILY_LIMIT) {
            throw new IllegalArgumentException("오늘은 " + BeautyLabService.DAILY_LIMIT + "번까지 물어볼 수 있어요.");
        }
        if (!voyageClient.isConfigured()) throw new IllegalStateException("VOYAGE_API_KEY가 설정되지 않았어요.");

        // 1. 질문 → 벡터 → 가까운 문단
        float[] q = voyageClient.embed(List.of(question), "query").get(0);

        RagChunkVO param = new RagChunkVO();
        param.setEmbedding(VoyageClient.toPgVector(q));
        param.setLimit(TOP_K);

        List<RagChunkVO> hits = regulationDao.searchChunks(param).stream()
                .filter(h -> h.getScore() != null && h.getScore() >= MIN_SCORE)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);

        // 2. 근거가 없으면 Claude를 부르지 않음
        if (hits.isEmpty()) {

            result.put("answer", "등록된 문서에서 관련 내용을 찾지 못했어요. 질문을 바꾸거나, 관리자에게 관련 문서 등록을 요청해주세요.");
            result.put("sources", List.of());
            result.put("found", false);

        } else {

            if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");

            StringBuilder user = new StringBuilder("질문: ").append(question).append("\n\n[발췌]\n");
            for (int i = 0; i < hits.size(); i++) {
                RagChunkVO h = hits.get(i);
                user.append("[").append(i + 1).append("] ").append(h.getTitle())
                        .append(h.getPage() == null ? "" : " (" + h.getPage() + "쪽)").append("\n")
                        .append(h.getContent()).append("\n\n");
            }

            Map<String, Object> response = claudeApiClient.createMessage(ANSWER_PROMPT, null,
                    List.of(Map.of("role", "user", "content", user.toString())), null, 1500);

            String answer = text(response);

            // 3. 답에서 실제로 인용한 번호
            Set<Integer> cited = new TreeSet<>();
            Matcher m = CITE.matcher(answer);
            while (m.find()) cited.add(Integer.parseInt(m.group(1)));

            List<Map<String, Object>> sources = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                RagChunkVO h = hits.get(i);
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("no", i + 1);
                s.put("title", h.getTitle());
                s.put("source", h.getSource());
                s.put("page", h.getPage());
                s.put("content", h.getContent());
                s.put("score", Math.round(h.getScore() * 1000) / 1000.0);
                s.put("cited", cited.contains(i + 1));
                sources.add(s);
            }

            result.put("answer", answer);
            result.put("sources", sources);
            result.put("found", true);

        }

        result.put("logId", saveLog(userId, question, result));
        result.put("remaining", Math.max(0, BeautyLabService.DAILY_LIMIT - countToday(userId)));

        return result;

    }

    // ============================================
    // 글자 추출 · 쪼개기
    // ============================================

    // 쪽별 글자 (PDF: PDFBox / 그 외: UTF-8 글자 파일 = 1쪽)
    List<String> extractPages(String fileName, byte[] bytes) {

        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        if (!name.endsWith(".pdf")) {
            return List.of(new String(bytes, StandardCharsets.UTF_8));
        }

        try (PDDocument pdf = Loader.loadPDF(bytes)) {

            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();

            for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(stripper.getText(pdf));
            }

            return pages;

        } catch (Exception e) {

            throw new IllegalArgumentException("PDF를 읽지 못했어요. (" + e.getMessage() + ")");

        }

    }

    record Chunk(int page, String text) {}

    // 쪽 → 문단(약 700자) : "제○조(" 앞에서 끊고, 길면 문장 경계에서 자르고, 앞 문단 끝 120자를 겹쳐 문맥 유지
    List<Chunk> chunk(List<String> pages) {

        List<Chunk> out = new ArrayList<>();

        for (int p = 0; p < pages.size(); p++) {

            String text = pages.get(p).replace(' ', ' ').replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
            if (text.isEmpty()) continue;

            StringBuilder buf = new StringBuilder();

            for (String part : ARTICLE.split(text)) {

                String piece = part.trim();
                if (piece.isEmpty()) continue;

                if (buf.length() > 0 && buf.length() + piece.length() > CHUNK_SIZE) {
                    flush(out, p + 1, buf);
                }

                // 조문 하나가 너무 길면 문장 단위로 나눔
                while (piece.length() > CHUNK_SIZE) {
                    int cut = lastBoundary(piece, CHUNK_SIZE);
                    buf.append(piece, 0, cut);
                    flush(out, p + 1, buf);
                    // 겹치는 부분은 단어 중간이 아니라 다음 띄어쓰기부터 (cut은 항상 350자보다 커서 앞으로 나아감)
                    int start = piece.indexOf(' ', cut - CHUNK_OVERLAP);
                    piece = piece.substring(start < 0 || start >= cut ? cut - CHUNK_OVERLAP : start).trim();
                }

                if (buf.length() > 0) buf.append('\n');
                buf.append(piece);

            }

            flush(out, p + 1, buf);

        }

        return out;

    }

    private void flush(List<Chunk> out, int page, StringBuilder buf) {

        String t = buf.toString().trim();
        if (t.replaceAll("\\s", "").length() >= 20) out.add(new Chunk(page, t));
        buf.setLength(0);

    }

    // max 이하에서 마지막 문장 경계 (없으면 max)
    private int lastBoundary(String s, int max) {

        for (int i = Math.min(max, s.length()) - 1; i > max / 2; i--) {
            char c = s.charAt(i);
            if (c == '\n' || c == '.' || c == '。') return i + 1;
        }

        return Math.min(max, s.length());

    }

    private int estimateTokens(String text) {

        return (int) Math.ceil(text.length() / 1.5) + 16;

    }

    // ============================================
    // 공통
    // ============================================

    @SuppressWarnings("unchecked")
    private String text(Map<String, Object> response) {

        StringBuilder sb = new StringBuilder();
        Object content = response == null ? null : response.get("content");

        if (content instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m && "text".equals(m.get("type"))) sb.append(m.get("text"));
            }
        }

        return sb.toString().trim();

    }

    private int countToday(Long userId) {

        BeautyLogVO param = new BeautyLogVO();
        param.setUserId(userId);
        param.setKind("ASK");

        return beautyLabDao.countToday(param);

    }

    private Long saveLog(Long userId, String question, Map<String, Object> result) {

        try {

            BeautyLogVO vo = new BeautyLogVO();
            vo.setUserId(userId);
            vo.setKind("ASK");
            vo.setInputType("TEXT");
            vo.setTitle(question.length() > 200 ? question.substring(0, 200) : question);
            vo.setResultJson(jsonMapper.writeValueAsString(result));
            beautyLabDao.insertLog(vo);

            return vo.getLogId();

        } catch (Exception e) {

            log.warn("규제 Q&A 기록 저장 실패: {}", e.getMessage());
            return null;

        }

    }
}
