package com.sklabs1037.sklabs1037.news.service;

import com.sklabs1037.sklabs1037.news.vo.NewsListVO;

import java.util.List;

/**
 * 메인 화면(뉴스 목록) Service
 */
public interface INewsListService {

    // 최신 뉴스 목록
    List<NewsListVO> getNewsList(NewsListVO vo);
}