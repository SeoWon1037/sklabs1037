key: code-predict
title: Spring 코드 · predict 패키지
topics: spring, predict
---
## 요약
🎯 오를까 내릴까

클래스 5개 · API 2개 · DB 표 5개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/predict`

## API
- GET /api/predict → overview()
- POST /api/predict/vote → vote()

## DB 표
- T_PREDICT_ITEM, T_PREDICT_VOTE, T_STOCK, T_STOCK_PRICE, T_USER

## 클래스

### PredictController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/predict/controller/PredictController.java
- 🎯 오를까 내릴까

### PredictDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/predict/dao/PredictDao.java
- 🎯 오를까 내릴까 DAO (SQL: resources/mapper/PredictMapper.xml)
- 메서드: selectCandidates · insertItem · selectRound · selectItem · upsertVote · selectUngraded · selectClose · selectPrevClose · gradeItem · gradeVotes · selectLastGradedDate · selectMyGraded · selectLeaderboard · selectBotRecord · selectCrowdRecord

### PredictScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/predict/scheduler/PredictScheduler.java
- 🎯 예측 게임 채점 (평일 21:10, 20:30 시세 수집이 끝난 뒤 → 진짜 종가로)

### PredictService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/predict/service/PredictService.java
- 🎯 오를까 내릴까 (매일 예측 게임)
- 라운드 = 거래일 D 하나
- 문제: 시가총액 상위 30종목 중 날짜로 섞어 고른 5종목 (그날 누구에게나 같은 문제)
- 질문: D 종가가 그 전 거래일 종가보다 오를까, 내릴까
- 투표: D 09:00(장 시작) 전까지, 마감 전에는 바꿀 수 있음
- 메서드: roundDate(now) · overview(userId) · vote(userId, itemId, pick) · gradeAll()

### PredictVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/predict/vo/PredictVO.java
- 🎯 오를까 내릴까 VO
- 사용 테이블: T_PREDICT_ITEM, T_PREDICT_VOTE, T_STOCK, T_STOCK_PRICE, T_USER
