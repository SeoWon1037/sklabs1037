package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.dao.StockMainDao;
import com.sklabs1037.sklabs1037.stock.vo.StockMainVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 주식 종목 화면 Service 구현
 * - 목록: SQL 2번으로 끝냄 (① 종목별 최신 시세 ② 전 종목 미니 차트 데이터)
 * - 상세: 종목 정보 + 일봉 (주봉/월봉은 화면에서 일봉을 묶어서 계산)
 */
@Service
public class StockMainServiceImpl implements IStockMainService {

    @Autowired
    private StockMainDao stockMainDao;

    @Override
    public List<StockMainVO> getDashboard(int days) {

        // 1. 종목별 최신 시세
        List<StockMainVO> stocks = stockMainDao.selectStockSummary();

        // 2. 전 종목 미니 차트 데이터를 한 번에 조회 → 종목 ID별로 묶기
        StockMainVO param = new StockMainVO();
        param.setDays(days);

        Map<Long, List<Map<String, Object>>> pricesByStock = new HashMap<>();

        for (StockMainVO p : stockMainDao.selectRecentPrices(param)) {

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", p.getTradeDate().toString());
            point.put("close", p.getClosePrice());
            point.put("volume", p.getVolume());

            pricesByStock.computeIfAbsent(p.getStockId(), k -> new ArrayList<>()).add(point);

        }

        // 3. 종목에 미니 차트 데이터 붙이기
        for (StockMainVO s : stocks) {
            s.setPrices(pricesByStock.getOrDefault(s.getStockId(), List.of()));
        }

        return stocks;

    }

    @Override
    public Map<String, Object> getDetail(String symbol) {

        StockMainVO param = new StockMainVO();
        param.setSymbol(symbol);

        StockMainVO stock = stockMainDao.selectStockDetail(param);
        if (stock == null) return null;

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