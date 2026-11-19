key: code-mock
title: Spring 코드 · mock 패키지
topics: spring, mock
---
## 요약
🩺 포트폴리오 AI 진단 (로그인)

클래스 17개 · API 18개 · DB 표 12개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/mock`

## API
- GET /api/mock/diagnosis → last()
- POST /api/mock/diagnosis → diagnose()
- GET /api/mock/reset-request → mine()
- POST /api/mock/reset-request → request()
- POST /api/mock/reset-request/cancel → cancel()
- GET /api/admin/resets → adminList()
- GET /api/admin/resets/count → count()
- POST /api/admin/resets/{id}/approve → approve()
- POST /api/admin/resets/{id}/reject → reject()
- GET /api/mock/seasons → seasons()
- GET /api/mock/account → account()
- GET /api/mock/quote → quote()
- GET /api/mock/trades → trades()
- POST /api/mock/order → order()
- POST /api/mock/orders/{orderId}/cancel → cancel()
- POST /api/mock/reset → reset()
- GET /api/mock/ranking → ranking()
- GET /api/mock/history → history()

## DB 표
- T_AI_TRADER, T_MOCK_ACCOUNT, T_MOCK_DIAGNOSIS, T_MOCK_HOLDING, T_MOCK_ORDER, T_MOCK_RESET_REQUEST, T_MOCK_SEASON, T_MOCK_SNAPSHOT, T_MOCK_TRADE, T_STOCK, T_STOCK_PRICE, T_USER

## 클래스

### MockDiagnosisController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/controller/MockDiagnosisController.java
- 🩺 포트폴리오 AI 진단 (로그인)

### MockResetController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/controller/MockResetController.java
- ♻️ 모의투자 계좌 초기화 신청
- 사용자 (로그인)
- 관리자

### MockSeasonController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/controller/MockSeasonController.java
- 🏆 모의투자 주간 시즌 명예의 전당 (누구나)

### MockTradeController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/controller/MockTradeController.java
- 모의투자 Controller
- → { success, loggedIn, account, holdings, pendingOrders, stocks, market }

### DiagnosisDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/dao/DiagnosisDao.java
- 🩺 포트폴리오 AI 진단 기록 DAO (SQL: resources/mapper/DiagnosisMapper.xml)
- 메서드: selectLast · countToday · insertDiagnosis

### MockTradeDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/dao/MockTradeDao.java
- 모의투자 Dao
- 메서드 이름 = MockTradeMapper.xml의 id
- 메서드: selectStocksByIds · selectStockIdBySymbol · selectFeaturedStockIds · insertAccount · selectAccount · selectAccountForUpdate · updateCash · resetAccount · selectHoldings · selectHolding · upsertHolding · deleteHolding · deleteAllHoldings · insertTrade · selectTrades · countTrades · deleteAllTrades · insertOrder · selectOrder · selectOrderForUpdate · updateOrderStatus · selectPendingOrders · countPendingOrders · sumPendingSellQuantity · sumPendingCash · selectMatchableOrders · selectExpiredOrders · deleteAllOrders · selectRankingAccounts · selectRankingHoldings · upsertSnapshot · selectSnapshots · insertSeasonWinner · selectSeasons · selectBenchmarkPrices · deleteSnapshots

### ResetRequestDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/dao/ResetRequestDao.java
- ♻️ 모의투자 계좌 초기화 신청 DAO (SQL: resources/mapper/ResetRequestMapper.xml)
- 메서드: insertRequest · selectMyPending · selectMyLastHandled · cancel · selectForAdmin · countPending · selectRequest · handle · selectAdminIds

### MockOrderScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/scheduler/MockOrderScheduler.java
- 모의투자 지정가 주문 스케줄러
- 정규장(평일 09:00~15:30) 1분마다: 지금 가격으로 조건이 맞는 주문 체결
- 평일 15:35: 오늘이 마지막인 미체결 주문 만료 (묶어둔 돈 돌려줌)
- 평일 15:40: 거래한 적 있는 계좌의 오늘 총자산 기록 (수익률 그래프, 이번 주 랭킹)
- 서버가 켜질 때: 서버가 꺼져 있어서 만료되지 못한 지난 주문 정리 (+ 장 마감 뒤면 오늘 기록)

### MockSeasonScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/scheduler/MockSeasonScheduler.java
- 🏆 모의투자 주간 시즌 마감 (토요일 09:00)
- 작업 내용: MockSeasonService.closeWeek / 기록·실패 메일: OpsJobs → JobRunService

### DiagnosisService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/DiagnosisService.java
- 🩺 포트폴리오 AI 진단 (모의투자 화면)
- 1. 서버가 숫자를 계산하고 규칙으로 점검 (분산 · 현금 비중 · 매도 승률 · 손익비 · 거래 빈도) → 좋음/주의/위험
- 2. Claude가 write_diagnosis 도구로 한 줄 요약 · 관찰 3개 · 해볼 습관 2개
- 종목 추천, "사라/팔아라" 금지 (습관 · 위험 관리 관점만)
- 3. 하루 3번까지, 마지막 결과는 저장해서 다시 열어도 보임
- 메서드: last(userId) · diagnose(userId)

### IMockTradeService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/IMockTradeService.java
- 모의투자 Service

### MockMarketHours · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/MockMarketHours.java
- 모의투자 장 시간 / 호가 단위 규칙
- 정규장: 평일 09:00 ~ 15:30 (한국 시간)
- 공휴일은 따로 계산하지 않음 (공휴일엔 시세가 안 바뀌어서 체결되지 않고, 장 마감 시각에 만료됨)

### MockPriceService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/MockPriceService.java
- 현재가 Service (모의투자 · 가격 알림 공통)
- 전 종목(약 3,000개)을 매번 조회하지 않고, 지금 필요한 종목만 조회
- (보유 종목, 미체결 주문, 알림, 주문 칸에서 고른 종목, 주요 종목)
- 토스 현재가는 한 번에 최대 200종목 → 200개씩 묶어서 호출
- 종목마다 10초 동안은 같은 가격을 재사용 → 화면 새로고침, 랭킹 조회가 많아도 토스 호출은 10초에 1번
- 메서드: getPrice(stockId) · getPrices(stockIds)

### MockSeasonService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/MockSeasonService.java
- 🏆 모의투자 주간 시즌 (작업 SEASON_CLOSE, 토요일 09:00 — 금요일 장 마감 기록까지 본 뒤)
- 그 주 수익률(지난주 마지막 장 마감 대비, MockTradeServiceImpl.weekStandings) TOP 3를 T_MOCK_SEASON에 기록
- 사람만 (🤖 AI 리그 참가자 제외), 수익률이 같으면 같은 등수 (3등 안이면 모두)
- 1등 👑 주간 챔피언, 1~3등 🏅 주간 TOP 3 배지 + 알림
- 다음 주 랭킹은 자동으로 새로 시작 (이번 주 = 지난주 장 마감 대비라서 따로 초기화 없음)
- 메서드: closeWeek() · recent(weeks)

### MockTradeServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/MockTradeServiceImpl.java
- 모의투자 Service 구현
- 주문 처리 (한 트랜잭션)
- 1. 계좌 행을 잠금 (SELECT ... FOR UPDATE) → 같은 사람의 주문·체결·취소는 한 줄로 서서 하나씩 처리
- 2. 현금 / 보유 수량 확인
- 3. 보유 종목(수량·평균 단가), 현금, 거래 내역을 같이 저장
- 메서드: getAccount(userId) · getTrades(userId, page, size) · order(userId, request) · cancelOrder(userId, orderId) · findFillableOrderIds() · fillOrder(orderId) · findExpiredOrderIds(upTo) · expireOrder(orderId) · reset(userId) · getRanking(userId, size, period) · weekStandings() · getHistory(userId) · snapshotAll(day) · getQuote(symbol)

### ResetRequestService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/service/ResetRequestService.java
- ♻️ 모의투자 계좌 초기화 신청 (관리자가 승인해야 초기화)
- 사용자: "초기화 신청"(이유 선택) → 기다리는 중 (한 사람당 1개) · 직접 취소 가능
- → 관리자에게 🔔 알림
- 관리자(/admin/resets): 승인 → 그 자리에서 초기화(보유·주문·거래·그래프 지우고 3,000만 원) / 거절(한마디)
- → 신청한 사람에게 🔔 알림
- 메서드: mine(userId) · request(userId, reason) · cancel(userId) · adminList() · pendingCount() · approve(adminId, id) · reject(adminId, id, note)

### MockTradeVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/mock/vo/MockTradeVO.java
- 모의투자 VO
- 사용 테이블: T_MOCK_ACCOUNT, T_MOCK_HOLDING, T_MOCK_TRADE, T_MOCK_ORDER (+ T_STOCK, T_STOCK_PRICE, T_USER)
