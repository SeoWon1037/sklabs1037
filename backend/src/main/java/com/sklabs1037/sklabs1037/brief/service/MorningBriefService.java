package com.sklabs1037.sklabs1037.brief.service;

import com.sklabs1037.sklabs1037.brief.dao.MorningBriefDao;
import com.sklabs1037.sklabs1037.brief.vo.MorningBriefVO;
import com.sklabs1037.sklabs1037.mock.dao.MockTradeDao;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.news.service.INewsSummaryService;
import com.sklabs1037.sklabs1037.news.vo.NewsSummaryVO;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.stock.service.IWatchlistService;
import com.sklabs1037.sklabs1037.stock.vo.WatchlistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아침 브리핑 메일 Service
 *
 * 평일 08:00 (MorningBriefScheduler → OpsJobs "MORNING_BRIEF")
 *   1. 오늘 아직 안 받은 신청자 목록
 *   2. 한 명씩: 먼저 "오늘 보냄"으로 표시(markSent = 1일 때만) → 메일 → 실패하면 되돌림
 *   3. 내용: 시장 AI 요약 + 관심 종목(어제 종가·등락, 주요 종목은 AI 뉴스 요약 한 줄) + 모의투자 계좌
 *
 * - 이미 만들어 둔 AI 뉴스 요약을 다시 쓰고 Claude를 새로 부르지 않음 → 신청자가 늘어도 AI 비용 0
 * - 모의투자는 계좌가 있는 회원만 (브리핑 때문에 계좌가 새로 생기지 않게)
 * - "나에게 지금 보내보기"는 10분에 한 번 (메일 폭탄 방지), 오늘 보냄 표시는 건드리지 않음
 */
@Slf4j
@Service
public class MorningBriefService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 테스트 발송 간격 (초)
    private static final long TEST_COOLDOWN = 600;

    // 관심 종목 최대 표시 개수
    private static final int MAX_WATCH = 10;

    @Autowired
    private MorningBriefDao morningBriefDao;

    @Autowired
    private IWatchlistService watchlistService;

    @Autowired
    private INewsSummaryService newsSummaryService;

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private MockTradeDao mockTradeDao;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${app.frontend-url}")
    private String siteUrl;

    // 회원별 마지막 테스트 발송 시각
    private final Map<Long, Instant> testSentAt = new ConcurrentHashMap<>();

    // ============================================
    // 마이페이지
    // ============================================
    public Map<String, Object> getStatus(Long userId) {

        MorningBriefVO me = morningBriefDao.selectMyBrief(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", me != null && "Y".equals(me.getEnabledYn()));
        result.put("hasEmail", me != null && hasEmail(me.getEmail()));
        result.put("lastSentDay", me == null || me.getLastSentDay() == null ? null : me.getLastSentDay().toString());

        return result;

    }

    public void setEnabled(Long userId, boolean enabled) {

        MorningBriefVO me = morningBriefDao.selectMyBrief(userId);
        if (me == null) throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        if (enabled && !hasEmail(me.getEmail())) throw new IllegalArgumentException("이메일이 없는 계정이라 메일을 받을 수 없어요.");

        MorningBriefVO vo = new MorningBriefVO();
        vo.setUserId(userId);
        vo.setEnabledYn(enabled ? "Y" : "N");
        morningBriefDao.upsertEnabled(vo);

    }

    // 나에게 지금 보내보기
    public void sendTest(Long userId) {

        MorningBriefVO me = morningBriefDao.selectMyBrief(userId);
        if (me == null || !hasEmail(me.getEmail())) throw new IllegalArgumentException("이메일이 없는 계정이라 메일을 받을 수 없어요.");

        Instant now = Instant.now();
        Instant before = testSentAt.get(userId);
        if (before != null && now.isBefore(before.plusSeconds(TEST_COOLDOWN))) {
            throw new IllegalArgumentException("테스트 메일은 10분에 한 번 보낼 수 있어요.");
        }
        testSentAt.put(userId, now);

        send(me.getEmail(), subject(LocalDate.now(KST)) + " (미리보기)", build(userId, me.getNickname()));

    }

    // ============================================
    // 평일 08:00
    // ============================================
    public JobOutcome sendAll() {

        LocalDate today = LocalDate.now(KST);

        MorningBriefVO param = new MorningBriefVO();
        param.setToday(today);

        List<MorningBriefVO> targets = morningBriefDao.selectTargets(param);
        int sent = 0;
        int failed = 0;
        String firstError = null;

        for (MorningBriefVO t : targets) {

            MorningBriefVO mark = new MorningBriefVO();
            mark.setUserId(t.getUserId());
            mark.setToday(today);

            // 먼저 "오늘 보냄" → 1이 아니면 다른 서버/실행이 이미 보냄
            if (morningBriefDao.markSent(mark) == 0) continue;

            try {

                send(t.getEmail(), subject(today), build(t.getUserId(), t.getNickname()));
                sent++;

            } catch (Exception e) {

                failed++;
                if (firstError == null) firstError = e.getMessage();
                log.warn("아침 브리핑 메일 실패 (다음 실행 때 다시): user={} {}", t.getUserId(), e.getMessage());
                morningBriefDao.revertSent(mark);

            }

        }

        log.info("아침 브리핑 메일: 대상 {}명, 보냄 {}명, 실패 {}명", targets.size(), sent, failed);

        return JobOutcome.of(targets.size(), failed,
                "보냄 " + sent + "명" + (firstError == null ? "" : " (첫 실패: " + firstError + ")"));

    }

    // ============================================
    // 메일 내용
    // ============================================
    String build(Long userId, String nickname) {

        StringBuilder sb = new StringBuilder();
        sb.append(nickname == null ? "" : nickname + "님, ").append("좋은 아침이에요 ☀️\n");

        // 1. 시장 한눈에
        NewsSummaryVO market = safeSummary(null);
        if (market != null && market.getSummary() != null) {
            sb.append("\n■ 시장 한눈에 (AI 뉴스 요약")
                    .append(market.getSummaryAt() == null ? "" : ", " + shortTime(market.getSummaryAt().toString()))
                    .append(")\n");
            for (String line : lines(market.getSummary(), 3)) sb.append("- ").append(line).append("\n");
        }

        // 2. 관심 종목
        List<WatchlistVO> watch = watchlistService.getWatchlist(userId);
        sb.append("\n■ 관심 종목");
        if (watch.isEmpty()) {
            sb.append("\n- 아직 관심 종목이 없어요. 주식 종목 화면에서 ☆를 눌러 추가하면 여기 나와요.\n");
        } else {
            LocalDate day = watch.stream().map(WatchlistVO::getTradeDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
            sb.append(day == null ? "" : " (" + day.getMonthValue() + "/" + day.getDayOfMonth() + " 종가)").append("\n");

            for (WatchlistVO w : watch.subList(0, Math.min(MAX_WATCH, watch.size()))) {

                sb.append("- ").append(w.getName()).append(" ")
                        .append(w.getClosePrice() == null ? "시세 없음" : won(w.getClosePrice()) + "원 " + rate(w.getChangeRate()))
                        .append("\n");

                NewsSummaryVO s = safeSummary(w.getStockId());
                if (s != null && s.getSummary() != null) {
                    List<String> first = lines(s.getSummary(), 1);
                    if (!first.isEmpty()) sb.append("  · ").append(first.get(0)).append("\n");
                }

            }

            if (watch.size() > MAX_WATCH) sb.append("- 외 ").append(watch.size() - MAX_WATCH).append("종목\n");
        }

        // 3. 모의투자 (계좌가 있는 회원만)
        appendMock(sb, userId);

        sb.append("\n바로가기\n")
                .append("- 오늘의 뉴스: ").append(siteUrl).append("/\n")
                .append("- 관심 종목: ").append(siteUrl).append("/stock\n")
                .append("- 모의투자: ").append(siteUrl).append("/mock\n")
                .append("\n※ SKLabs의 시세·뉴스·AI 요약은 참고용이며, 투자 판단은 본인 책임입니다.\n")
                .append("※ 메일을 그만 받으려면 마이페이지 > 아침 브리핑 메일에서 끌 수 있어요.");

        return sb.toString();

    }

    @SuppressWarnings("unchecked")
    private void appendMock(StringBuilder sb, Long userId) {

        try {

            if (mockTradeDao.selectAccount(userId) == null) return;

            Map<String, Object> acc = mockTradeService.getAccount(userId);
            Map<String, Object> account = (Map<String, Object>) acc.get("account");
            List<Map<String, Object>> holdings = (List<Map<String, Object>>) acc.get("holdings");

            sb.append("\n■ 내 모의투자\n")
                    .append("- 총자산 ").append(won(account.get("totalAsset"))).append("원 (")
                    .append(signed(account.get("returnRate"))).append("%)\n");

            if (holdings != null && !holdings.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map<String, Object> h : holdings.subList(0, Math.min(5, holdings.size()))) {
                    parts.add(h.get("name") + " " + signed(h.get("returnRate")) + "%");
                }
                sb.append("- 보유: ").append(String.join(", ", parts)).append(holdings.size() > 5 ? " 외" : "").append("\n");
            } else {
                sb.append("- 보유 종목 없음 (현금 100%)\n");
            }

        } catch (Exception e) {

            log.warn("아침 브리핑: 모의투자 정보 생략 (user={}): {}", userId, e.getMessage());

        }

    }

    private void send(String to, String subject, String body) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);

    }

    // "[SKLabs] 10/21(화) 아침 브리핑"
    private String subject(LocalDate day) {

        return "[SKLabs] " + day.getMonthValue() + "/" + day.getDayOfMonth()
                + "(" + day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ") 아침 브리핑 ☀️";

    }

    private NewsSummaryVO safeSummary(Long stockId) {

        try {
            return newsSummaryService.getSummary(stockId);
        } catch (Exception e) {
            return null;
        }

    }

    // 요약 본문 → 앞의 "-" 뺀 줄 n개
    private List<String> lines(String text, int max) {

        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .map(l -> l.replaceFirst("^[-•·*]\\s*", ""))
                .filter(l -> !l.isEmpty())
                .limit(max)
                .toList();

    }

    // "2026-10-20T16:30:12" → "10/20 16:30"
    private String shortTime(String iso) {

        if (iso.length() < 16) return iso;

        return Integer.parseInt(iso.substring(5, 7)) + "/" + Integer.parseInt(iso.substring(8, 10)) + " " + iso.substring(11, 16);

    }

    private boolean hasEmail(String email) {

        return email != null && email.contains("@");

    }

    private String won(Object value) {

        if (value == null) return "-";

        return String.format("%,d", new BigDecimal(String.valueOf(value)).longValue());

    }

    private String rate(BigDecimal value) {

        if (value == null) return "";

        int sign = value.signum();

        return (sign > 0 ? "▲" : sign < 0 ? "▼" : "") + value.abs().setScale(2, java.math.RoundingMode.HALF_UP) + "%";

    }

    private String signed(Object value) {

        if (value == null) return "0.00";

        BigDecimal d = new BigDecimal(String.valueOf(value)).setScale(2, java.math.RoundingMode.HALF_UP);

        return (d.signum() > 0 ? "+" : "") + d.toPlainString();

    }
}
