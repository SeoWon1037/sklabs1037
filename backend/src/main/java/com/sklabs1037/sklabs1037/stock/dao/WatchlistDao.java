package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.WatchlistVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 관심 종목 Dao
 * - 메서드 이름 = WatchlistMapper.xml의 id
 */
@Mapper
public interface WatchlistDao {

    // 내 관심 종목 + 최신 시세
    List<WatchlistVO> selectWatchlist(Long userId);

    // 추가 (없는 종목이거나 이미 있으면 0)
    int insertWatchlist(WatchlistVO vo);

    // 삭제
    int deleteWatchlist(WatchlistVO vo);
}