key: code-stock
title: Spring 코드 · stock 패키지
topics: spring, stock
---
## 요약
관리자: 토론방 신고 관리

클래스 26개 · API 22개 · DB 표 11개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/stock`

## API
- GET /api/admin/reports → list()
- GET /api/admin/reports/count → count()
- POST /api/admin/reports/{postId} → handle()
- GET /api/stock/board/stream → stream()
- GET /api/stock/board → list()
- GET /api/stock/board/recent → recent()
- POST /api/stock/board → writePost()
- DELETE /api/stock/board/{postId} → deletePost()
- POST /api/stock/board/{postId}/like → like()
- POST /api/stock/board/{postId}/report → report()
- GET /api/stock/board/{postId}/comments → comments()
- POST /api/stock/board/{postId}/comments → writeComment()
- DELETE /api/stock/board/comments/{commentId} → deleteComment()
- GET /api/stock/brief → get()
- POST /api/stock/brief → make()
- GET /api/stock/dashboard → dashboard()
- GET /api/stock/list → list()
- GET /api/stock/search → search()
- GET /api/stock/detail → detail()
- GET /api/watchlist → list()
- POST /api/watchlist → add()
- DELETE /api/watchlist → remove()

## DB 표
- T_NEWS, T_STOCK, T_STOCK_BRIEF, T_STOCK_COMMENT, T_STOCK_DOC, T_STOCK_POST, T_STOCK_POST_LIKE, T_STOCK_POST_REPORT, T_STOCK_PRICE, T_USER, T_WATCHLIST

## 클래스

### BoardReportAdminController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/controller/BoardReportAdminController.java
- 관리자: 토론방 신고 관리

### StockBoardController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/controller/StockBoardController.java
- 종목 토론방 Controller

### StockBriefController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/controller/StockBriefController.java
- 🧾 종목 한 장 요약

### StockMainController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/controller/StockMainController.java
- 주식 종목 화면 Controller

### WatchlistController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/controller/WatchlistController.java
- 관심 종목 Controller
- 추가/삭제는 로그인한 사람만, 본인 목록만

### StockBoardDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/dao/StockBoardDao.java
- 종목 토론방 Dao
- 메서드 이름 = StockBoardMapper.xml의 id
- 메서드: selectPostList · selectRecentPostList · countPost · selectOpinionCount · selectPost · countRecentPost · insertPost · deletePost · selectCommentList · selectComment · countRecentComment · insertComment · deleteComment · insertLike · deleteLike · countLike · insertReport · countOpenReport · selectReportedPosts · countReportedPosts · handleReports · selectPostAny

### StockBriefDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/dao/StockBriefDao.java
- 🧾 종목 한 장 요약 DAO (SQL: resources/mapper/StockBriefMapper.xml)
- 메서드: selectStock · selectCloses · selectNews · selectBoardCounts · selectRecentPosts · selectBrief · upsertBrief

### StockCollectDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/dao/StockCollectDao.java
- 시세 수집 Dao
- 메서드 이름 = StockCollectMapper.xml의 id
- 메서드: selectTargetStocks · selectStocksWithoutPrice · selectStaleStocks · countPrices · selectStocksNeedingHistory · upsertStockPrice · updateSharesOutstanding · upsertStockMaster · upsertStockPriceBatch · upsertStockMasterBatch · updateSharesBatch · fillMissingLastPrice · markHistoryDone · deactivateStock · deactivateMissing · updateLatestPrice

### StockMainDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/dao/StockMainDao.java
- 주식 종목 화면 Dao
- 메서드 이름 = StockMainMapper.xml의 id
- 메서드: selectStockSummary · selectStockPage · countStockPage · selectStockSearch · selectRecentPrices · selectStockDetail · selectCandles

### WatchlistDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/dao/WatchlistDao.java
- 관심 종목 Dao
- 메서드 이름 = WatchlistMapper.xml의 id
- 메서드: selectWatchlist · insertWatchlist · deleteWatchlist

### StockCollectScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/scheduler/StockCollectScheduler.java
- 시세 수집 스케줄러 (작업 내용은 OpsJobs, 실행 기록·실패 메일은 JobRunService)
- 앱 시작 시 (별도 스레드) STOCK_STARTUP: 종목 목록 동기화 → 상장 주식 수 → 시세 없는 종목은 현재가 임시로
- → 최근 거래일 시세가 빠진 종목(20:30 수집을 놓친 날) daily-count일치 → 시세 이력이 부족한 종목만 initial-count일치 → 상장 주식 수
- (이력은 주요 종목 → 코스피 → 큰 종목 순이라 목록이 위에서부터 차례로 채워짐)
- 평일 20:30 STOCK_DAILY: 종목 목록 동기화 → 전 종목(약 3,900개) 최근 daily-count일 수집 (겹치는 날짜는 UPSERT) → 상장 주식 수

### BoardLiveHub · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/BoardLiveHub.java
- 실시간 토론방 (SSE: Server-Sent Events)
- 왜 WebSocket이 아니라 SSE인지
- 토론방은 "글 쓰기"는 지금처럼 POST로 보내고, 서버 → 화면 방향으로 "새 글 왔어요" 알림만 있으면 됨 (한 방향)
- SSE는 그냥 HTTP 응답을 끊지 않고 조금씩 보내는 방식 → nginx·세션·로그인을 그대로 쓰고, 끊기면 브라우저가 알아서 다시 붙음
- WebSocket은 양방향(채팅·게임)에 맞고, 프로토콜이 달라서 nginx 업그레이드 설정·별도 인증·재접속 코드가 더 필요

### IStockBoardService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/IStockBoardService.java
- 종목 토론방 Service
- 입력값이 잘못되면 IllegalArgumentException (메시지를 그대로 화면에 보여줌)
- 삭제 권한이 없으면 SecurityException

### IStockCollectService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/IStockCollectService.java
- 시세 수집 Service

### IStockMainService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/IStockMainService.java
- 주식 종목 화면 Service

### IWatchlistService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/IWatchlistService.java
- 관심 종목 Service

### StockBoardServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/StockBoardServiceImpl.java
- 종목 토론방 Service 구현
- 메서드: getPosts(stockId, page, size, viewerId, sort) · getRecentPosts(size) · countPosts(stockId) · getOpinionCount(stockId) · writePost(userId, stockId, opinion, content) · deletePost(userId, admin, postId) · getComments(postId) · writeComment(userId, postId, content) · deleteComment(userId, admin, commentId) · toggleLike(userId, postId) · report(userId, postId, reason, memo) · getReportedPosts() · countReportedPosts() · handleReport(adminId, postId, action)

### StockBriefService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/StockBriefService.java
- 🧾 종목 한 장 요약 (종목 상세 맨 위)
- 재료: 최근 7일 이 종목 뉴스 제목 · 토론방 분위기(오를 것/내릴 것, 최근 글) · 가격 흐름(서버가 계산한 1일/5일/20일 등락)
- Claude가 write_brief 도구로 📰 뉴스 · 💬 토론 분위기 · 📈 가격 흐름 한 줄씩 (투자 판단 · 전망 금지)
- 종목마다 3시간 저장 → 그 사이엔 누구나 저장된 요약을 봄 / 새로 만들기는 로그인 · 하루 전체 300번까지
- 메서드: get(stockId) · make(stockId)

### StockCollectServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/StockCollectServiceImpl.java
- 시세 수집 Service 구현 (데이터 파이프라인)
- 0. 종목 목록 : 토스 /api/v1/stocks/all → 코스피·코스닥 주식(보통주·우선주) + ETF 약 3,000개를 T_STOCK에 UPSERT
- Extract   : 토스증권 API 일봉 조회 (한 번에 한 종목)
- Transform : 문자열 가격 → BigDecimal, ISO 시간 → 날짜
- Load      : T_STOCK_PRICE UPSERT (다시 받아도 중복 없음) + T_STOCK 최신 종가 복사
- 메서드: syncStockMaster() · collectAll(count) · isEmpty() · collectMissingHistory(minRows, count) · collectHistoryNow(stockId, symbol) · updateSharesOutstanding() · collectStale(count) · refreshIfStale(stockId, symbol, lastDate) · fillMissingPrices()

### StockMainServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/StockMainServiceImpl.java
- 주식 종목 화면 Service 구현
- 목록: SQL 2번으로 끝냄 (① 시가총액 상위 + 주요 종목의 최신 시세 ② 그 종목들의 미니 차트 데이터)
- → 전 종목(약 3,000개)이 등록돼 있어도 화면에는 일부만, 나머지는 검색으로
- 검색: 이름 일부 / 종목코드 앞자리 (미니 차트 없이)
- 상세: 종목 정보 + 일봉 (주봉/월봉은 화면에서 일봉을 묶어서 계산)
- 메서드: getDashboard(days, limit) · getPage(market, sort, offset, limit, days) · search(query, limit) · getDetail(symbol)

### WatchlistServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/service/WatchlistServiceImpl.java
- 관심 종목 Service 구현
- 메서드: getWatchlist(userId) · add(userId, stockId) · remove(userId, stockId)

### CollectResult · record
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/vo/CollectResult.java
- 여러 종목 시세 수집 결과
- stocks: 대상 종목 수 / saved: 저장한 일봉 건수 / failed: 실패한 종목 수 / firstError: 첫 실패 이유 (운영 화면·메일용)
- skipped: 토스에 더 이상 없는 종목 (상장폐지·코드 변경 → "stock-not-found") → 실패로 안 치고 수집 대상에서 뺌

### StockBoardVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/vo/StockBoardVO.java
- 종목 토론방 VO (글 + 댓글 같이 사용)

### StockCollectVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/vo/StockCollectVO.java
- 시세 수집(데이터 파이프라인)에서 쓰는 값을 담는 VO
- 사용 테이블: T_STOCK, T_STOCK_PRICE

### StockMainVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/vo/StockMainVO.java
- 주식 종목 화면 VO
- 사용 테이블: T_STOCK, T_STOCK_PRICE, T_STOCK_DOC

### WatchlistVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/stock/vo/WatchlistVO.java
- 관심 종목 VO
