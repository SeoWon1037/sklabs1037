package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.NewsListDao;
import com.sklabs1037.sklabs1037.vo.NewsListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 메인 화면(뉴스 목록) Service 구현
 */
@Service
public class NewsListServiceImpl implements INewsListService {

    @Autowired
    private NewsListDao newsListDao;

    @Override
    public List<NewsListVO> getNewsList(NewsListVO vo) {

        return newsListDao.selectNewsList(vo);

    }
}