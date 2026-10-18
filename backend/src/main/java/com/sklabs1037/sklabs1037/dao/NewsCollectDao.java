package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.NewsCollectVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 뉴스 수집 Dao
 * - 메서드 이름 = NewsCollectMapper.xml의 id
 */
@Mapper
public interface NewsCollectDao {

    // 종목 뉴스 수집 대상
    List<NewsCollectVO> selectTargetStocks();

    // 뉴스 저장 (새로 저장되면 1, 이미 있으면 0)
    int insertNews(NewsCollectVO vo);
}