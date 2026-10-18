package com.sklabs1037.sklabs1037.news.dao;

import com.sklabs1037.sklabs1037.news.vo.NewsListVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 메인 화면(뉴스 목록) Dao
 * - 메서드 이름 = NewsListMapper.xml의 id
 */
@Mapper
public interface NewsListDao {

    // 최신 뉴스 목록
    List<NewsListVO> selectNewsList(NewsListVO vo);
}