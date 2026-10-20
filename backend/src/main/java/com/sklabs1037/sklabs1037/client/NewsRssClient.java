package com.sklabs1037.sklabs1037.client;

import com.sklabs1037.sklabs1037.news.vo.NewsCollectVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Google News RSS 클라이언트 (뉴스 검색)
 * - GET https://news.google.com/rss/search?q=검색어&hl=ko&gl=KR&ceid=KR:ko
 * - 응답: RSS(XML) → <item> 마다 title, link, pubDate, source(언론사)
 * - API 키가 필요 없음
 */
@Slf4j
@Component
public class NewsRssClient {

    private static final String RSS_URL = "https://news.google.com/rss/search?q={q}&hl=ko&gl=KR&ceid=KR:ko";

    // RSS 발행 시각 형식 (예: Sat, 18 Oct 2026 01:00:00 GMT)
    private static final DateTimeFormatter RSS_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RestClient restClient = RestClients.withTimeout(15);

    // 검색어로 최근 7일 뉴스 조회 → 최신순 maxCount개
    public List<NewsCollectVO> search(String query, int maxCount) throws Exception {

        String xml = restClient.get()
                .uri(RSS_URL, query + " when:7d")   // when:7d = 최근 7일 기사만
                .header("User-Agent", "Mozilla/5.0 (compatible; SKLabsNewsBot/1.0)")
                .retrieve()
                .body(String.class);

        if (xml == null || xml.isBlank()) return List.of();

        NodeList items = parseXml(xml).getElementsByTagName("item");
        List<NewsCollectVO> list = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {

            Element item = (Element) items.item(i);

            String title = text(item, "title");
            String link  = text(item, "link");
            String date  = text(item, "pubDate");
            String press = text(item, "source");

            // 필수 값이 없거나 주소가 너무 긴 기사는 건너뜀
            if (title.isEmpty() || link.isEmpty() || date.isEmpty() || link.length() > 1000) continue;

            // 제목 끝에 붙은 " - 언론사" 떼기
            if (!press.isEmpty() && title.endsWith(" - " + press)) {
                title = title.substring(0, title.length() - press.length() - 3);
            }

            // 제목 끝에 남은 공백, "|" 정리
            title = title.replaceAll("[\\s|]+$", "");

            try {

                NewsCollectVO vo = new NewsCollectVO();
                vo.setTitle(cut(title, 500));
                vo.setLink(link);
                vo.setPress(cut(press, 100));
                vo.setPubDate(ZonedDateTime.parse(date, RSS_DATE).withZoneSameInstant(SEOUL).toLocalDateTime());

                list.add(vo);

            } catch (Exception e) {

                // 날짜 형식이 이상한 기사 하나 때문에 전체가 멈추지 않게
                log.debug("뉴스 항목 건너뜀: {}", title);

            }

        }

        // 최신 기사부터 maxCount개
        list.sort(Comparator.comparing(NewsCollectVO::getPubDate).reversed());

        return (list.size() > maxCount) ? list.subList(0, maxCount) : list;

    }

    // XML 문자열 → Document (외부 엔티티 차단: XXE 공격 방지)
    private Document parseXml(String xml) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);

        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

    }

    // <item> 안의 태그 글자 꺼내기 (없으면 빈 문자열)
    private String text(Element parent, String tag) {

        NodeList nodes = parent.getElementsByTagName(tag);

        return (nodes.getLength() == 0) ? "" : nodes.item(0).getTextContent().trim();

    }

    // 최대 길이로 자르기 (DB 컬럼 길이 초과 방지)
    private String cut(String value, int max) {

        return (value.length() > max) ? value.substring(0, max) : value;

    }
}