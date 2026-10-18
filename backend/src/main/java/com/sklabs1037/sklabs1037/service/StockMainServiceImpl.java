package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.StockMainDao;
import com.sklabs1037.sklabs1037.vo.StockMainVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 메인 화면(종목 대시보드) Service 구현
 * - SQL 2번으로 끝냄: ① 종목별 최신 시세 ② 전 종목 차트 데이터 (종목마다 따로 조회하지 않음)
 */
@Service
public class StockMainServiceImpl implements IStockMainService {

    @Autowired
    private StockMainDao stockMainDao;

    @Override
    public List<StockMainVO> getDashboard(int days) {

        // 1. 종목별 최신 시세
        List<StockMainVO> stocks = stockMainDao.selectStockSummary();

        // 2. 전 종목 차트 데이터를 한 번에 조회 → 종목 ID별로 묶기
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

        // 3. 종목에 차트 데이터 붙이기
        for (StockMainVO s : stocks) {
            s.setPrices(pricesByStock.getOrDefault(s.getStockId(), List.of()));
        }

        return stocks;

    }
}