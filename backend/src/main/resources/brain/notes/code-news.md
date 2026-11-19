key: code-news
title: Spring 코드 · news 패키지
topics: spring, news
---
## 요약
메인 화면(뉴스 목록) Controller

클래스 15개 · API 2개 · DB 표 3개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/news`

## API
- GET /api/news → list()
- GET /api/news/summary → summary()

## DB 표
- T_NEWS, T_NEWS_SUMMARY, T_STOCK

## 클래스

### NewsCollectScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/controller/NewsCollectScheduler.java
- 뉴스 수집 스케줄러
- 앱이 켜지면 1번 바로 수집 (중복 기사는 DB가 걸러줌)
- 이후 30분마다 수집

### NewsListController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/controller/NewsListController.java
- 메인 화면(뉴스 목록) Controller
- category, stockId 는 선택 (없으면 전체)
- stockId 가 없으면 시장 요약

### NewsCollectDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/dao/NewsCollectDao.java
- 뉴스 수집 Dao
- 메서드 이름 = NewsCollectMapper.xml의 id
- 메서드: selectTargetStocks · insertNews

### NewsListDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/dao/NewsListDao.java
- 메인 화면(뉴스 목록) Dao
- 메서드 이름 = NewsListMapper.xml의 id
- 메서드: selectNewsList

### NewsSummaryDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/dao/NewsSummaryDao.java
- AI 뉴스 요약 Dao
- 메서드 이름 = NewsSummaryMapper.xml의 id
- 메서드: selectTargetStocks · selectRecentTitles · upsertSummary · deleteSummary · countSummarySince · selectSummary

### NewsSummaryScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/scheduler/NewsSummaryScheduler.java
- AI 뉴스 요약 스케줄러
- 매일 07:30, 16:30 (장 시작 전 / 장 마감 후)
- 앱이 켜질 때 최근 6시간 안에 만든 요약이 없으면 1번 바로 요약
- (배포할 때마다 다시 요약하면 API 비용이 들어서, 최근 요약이 있으면 건너뜀)

### INewsCollectService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/service/INewsCollectService.java
- 뉴스 수집 Service

### INewsListService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/service/INewsListService.java
- 메인 화면(뉴스 목록) Service

### INewsSummaryService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/service/INewsSummaryService.java
- AI 뉴스 요약 Service

### NewsCollectServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/service/NewsCollectServiceImpl.java
- 뉴스 수집 Service 구현 (데이터 파이프라인)
- Extract   : Google News RSS 검색
- Transform : 제목 정리, 발행 시각 → 한국 시간 (NewsRssClient)
- Load      : T_NEWS 저장 (기사 주소 기준 중복 제외)
- 검색어 하나가 실패해도 나머지는 계속 수집 (검색어별 try/catch)
- 메서드: collectAll()

### NewsListServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/service/NewsListServiceImpl.java
- 메인 화면(뉴스 목록) Service 구현
- 메서드: getNewsList(vo)

### NewsSummaryServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/service/NewsSummaryServiceImpl.java
- AI 뉴스 요약 Service 구현
- 1. 시장 / 종목별로 최근 N시간 뉴스 제목을 모음 (T_NEWS)
- 2. 제목 목록을 Claude에 보내 3줄 요약을 받음
- 3. T_NEWS_SUMMARY 에 저장 (대상마다 1줄, 덮어쓰기)
- 종목 요약은 제목에 종목명이 들어간 기사만 사용 (검색 결과에 섞인 다른 기사 제외)
- 메서드: summarizeAll() · hasRecentSummary(withinHours) · getSummary(stockId)

### NewsCollectVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/vo/NewsCollectVO.java
- 뉴스 수집 VO
- 사용 테이블: T_NEWS, T_STOCK

### NewsListVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/vo/NewsListVO.java
- 메인 화면(뉴스 목록) VO
- 사용 테이블: T_NEWS, T_STOCK

### NewsSummaryVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/news/vo/NewsSummaryVO.java
- AI 뉴스 요약 VO
- 요약 대상 조회, 뉴스 제목 조회, 요약 저장/조회에 같이 사용
