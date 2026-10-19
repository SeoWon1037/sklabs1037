package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.dao.StockMainDao;
import com.sklabs1037.sklabs1037.stock.vo.StockMainVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 주식 종목 화면 Service 구현
 * - 목록: SQL 2번으로 끝냄 (① 시가총액 상위 + 주요 종목의 최신 시세 ② 그 종목들의 미니 차트 데이터)
 *   → 전 종목(약 3,000개)이 등록돼 있어도 화면에는 일부만, 나머지는 검색으로
 * - 검색: 이름 일부 / 종목코드 앞자리 (미니 차트 없이)
 * - 상세: 종목 정보 + 일봉 (주봉/월봉은 화면에서 일봉을 묶어서 계산)
 *   시세가 아직 없는 종목(새로 등록된 종목)은 열 때 바로 토스에서 받아옴
 */
@Service
public class StockMainServiceImpl implements IStockMainService {

    @Autowired
    private StockMainDao stockMainDao;

    @Autowired
    private IStockCollectService stockCollectService;

    @Override
    public List<StockMainVO> getDashboard(int days, int limit) {

        // 1. 시가총액 상위 + 주요 종목의 최신 시세
        StockMainVO listParam = new StockMainVO();
        listParam.setLimit(limit);

        List<StockMainVO> stocks = stockMainDao.selectStockSummary(listParam);

        // 2. 미니 차트
        attachPrices(stocks, days);

        return stocks;

    }

    @Override
    public Map<String, Object> getPage(String market, int offset, int limit, int days) {

        StockMainVO param = new StockMainVO();
        param.setMarketFilter(market);
        param.setOffset(offset);
        param.setLimit(limit);

        List<StockMainVO> stocks = stockMainDao.selectStockPage(param);
        attachPrices(stocks, days);

        Map<String, Object> result = new HashMap<>();
        result.put("stocks", stocks);
        result.put("total", stockMainDao.countStockPage(param));

        return result;

    }

    // 종목들의 최근 N거래일 종가를 한 번에 조회해서 붙이기 (미니 차트)
    private void attachPrices(List<StockMainVO> stocks, int days) {

        if (stocks.isEmpty()) return;

        // 한 번에 조회 → 종목 ID별로 묶기
        StockMainVO param = new StockMainVO();
        param.setDays(days);
        param.setStockIds(stocks.stream().map(StockMainVO::getStockId).toList());

        Map<Long, List<Map<String, Object>>> pricesByStock = new HashMap<>();

        for (StockMainVO p : stockMainDao.selectRecentPrices(param)) {

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", p.getTradeDate().toString());
            point.put("close", p.getClosePrice());
            point.put("volume", p.getVolume());

            pricesByStock.computeIfAbsent(p.getStockId(), k -> new ArrayList<>()).add(point);

        }

        // 종목에 미니 차트 데이터 붙이기
        for (StockMainVO s : stocks) {
            s.setPrices(pricesByStock.getOrDefault(s.getStockId(), List.of()));
        }

    }

    @Override
    public List<StockMainVO> search(String query, int limit) {

        StockMainVO param = new StockMainVO();
        param.setQuery(query);
        param.setLimit(limit);

        return stockMainDao.selectStockSearch(param);

    }

    @Override
    public Map<String, Object> getDetail(String symbol) {

        StockMainVO param = new StockMainVO();
        param.setSymbol(symbol);

        StockMainVO stock = stockMainDao.selectStockDetail(param);
        if (stock == null) return null;

        // 시세가 아직 없는 종목 → 지금 토스에서 받기 (실패해도 화면은 "시세 없음"으로)
        if (stockMainDao.selectCandles(param).isEmpty()) {

            try {
                stockCollectService.collectHistoryNow(stock.getStockId(), stock.getSymbol());
            } catch (Exception e) {
                // 토스 오류여도 종목 정보는 보여줌
            }

        }

        // 화면에서 쓰기 편한 짧은 이름으로
        List<Map<String, Object>> candles = new ArrayList<>();

        for (StockMainVO c : stockMainDao.selectCandles(param)) {

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", c.getTradeDate().toString());
            row.put("open", c.getOpenPrice());
            row.put("high", c.getHighPrice());
            row.put("low", c.getLowPrice());
            row.put("close", c.getClosePrice());
            row.put("volume", c.getVolume());
            candles.add(row);

        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stockId", stock.getStockId());
        info.put("symbol", stock.getSymbol());
        info.put("name", stock.getName());
        info.put("market", stock.getMarket());
        info.put("docTitle", stock.getDocTitle());
        info.put("docContent", stock.getDocContent());

        Map<String, Object> result = new HashMap<>();
        result.put("stock", info);
        result.put("candles", candles);

        return result;

    }
}