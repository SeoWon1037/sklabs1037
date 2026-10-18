package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.vo.WatchlistVO;

import java.util.List;

/**
 * 관심 종목 Service
 */
public interface IWatchlistService {

    // 내 관심 종목 + 최신 시세
    List<WatchlistVO> getWatchlist(Long userId);

    // 추가 (성공하면 true, 없는 종목이거나 이미 있으면 false)
    boolean add(Long userId, Long stockId);

    // 삭제
    boolean remove(Long userId, Long stockId);
}