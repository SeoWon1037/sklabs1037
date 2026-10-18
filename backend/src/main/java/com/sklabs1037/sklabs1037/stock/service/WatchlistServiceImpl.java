package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.dao.WatchlistDao;
import com.sklabs1037.sklabs1037.stock.vo.WatchlistVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 관심 종목 Service 구현
 */
@Service
public class WatchlistServiceImpl implements IWatchlistService {

    @Autowired
    private WatchlistDao watchlistDao;

    @Override
    public List<WatchlistVO> getWatchlist(Long userId) {

        return watchlistDao.selectWatchlist(userId);

    }

    @Override
    public boolean add(Long userId, Long stockId) {

        return watchlistDao.insertWatchlist(param(userId, stockId)) > 0;

    }

    @Override
    public boolean remove(Long userId, Long stockId) {

        return watchlistDao.deleteWatchlist(param(userId, stockId)) > 0;

    }

    private WatchlistVO param(Long userId, Long stockId) {

        WatchlistVO vo = new WatchlistVO();
        vo.setUserId(userId);
        vo.setStockId(stockId);

        return vo;

    }
}