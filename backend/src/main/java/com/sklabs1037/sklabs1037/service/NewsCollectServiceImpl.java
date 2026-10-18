package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.client.NewsRssClient;
import com.sklabs1037.sklabs1037.dao.NewsCollectDao;
import com.sklabs1037.sklabs1037.vo.NewsCollectVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 뉴스 수집 Service 구현 (데이터 파이프라인)
 *
 *   Extract   : Google News RSS 검색
 *   Transform : 제목 정리, 발행 시각 → 한국 시간 (NewsRssClient)
 *   Load      : T_NEWS 저장 (기사 주소 기준 중복 제외)
 *
 * - 검색어 하나가 실패해도 나머지는 계속 수집 (검색어별 try/catch)
 */
@Slf4j
@Service
public class NewsCollectServiceImpl implements INewsCollectService {

    // 시장 전체 뉴스 검색어
    private static final List<String> MARKET_QUERIES = List.of("코스피", "증시");

    @Autowired
    private NewsRssClient newsRssClient;

    @Autowired
    private NewsCollectDao newsCollectDao;

    @Value("${news.collect.max-per-query}")
    private int maxPerQuery;

    @Override
    public int collectAll() {

        int saved = 0;

        // 1. 시장 뉴스
        for (String query : MARKET_QUERIES) {
            saved += collect(query, null, "MARKET");
        }

        // 2. 종목 뉴스 ("삼성전자 주가"처럼 검색해서 주식 관련 기사 위주로)
        for (NewsCollectVO stock : newsCollectDao.selectTargetStocks()) {
            saved += collect(stock.getName() + " 주가", stock.getStockId(), "STOCK");
        }

        log.info("뉴스 수집 완료: 신규 {}건", saved);

        return saved;

    }

    // 검색어 하나 수집
    private int collect(String query, Long stockId, String category) {

        try {

            int saved = 0;

            for (NewsCollectVO vo : newsRssClient.search(query, maxPerQuery)) {

                vo.setStockId(stockId);
                vo.setCategory(category);

                saved += newsCollectDao.insertNews(vo);

            }

            log.info("뉴스 수집: {} → 신규 {}건", query, saved);

            return saved;

        } catch (Exception e) {

            log.warn("뉴스 수집 실패: {}", query, e);

            return 0;

        }

    }
}