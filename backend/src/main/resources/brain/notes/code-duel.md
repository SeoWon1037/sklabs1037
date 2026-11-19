key: code-duel
title: Spring 코드 · duel 패키지
topics: spring, duel
---
## 요약
⚔️ 친구 대결

클래스 4개 · API 5개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/duel`

## API
- GET /api/duel → mine()
- POST /api/duel → create()
- GET /api/duel/{code} → view()
- POST /api/duel/{code}/accept → accept()
- POST /api/duel/{code}/cancel → cancel()

## DB 표
- T_DUEL, T_USER

## 클래스

### DuelController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/duel/controller/DuelController.java
- ⚔️ 친구 대결

### DuelDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/duel/dao/DuelDao.java
- ⚔️ 친구 대결 DAO (SQL: resources/mapper/DuelMapper.xml)
- 메서드: insertDuel · selectByCode · selectMine · countOpen · accept · cancel · selectToSettle · settle · expireWaiting

### DuelScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/duel/scheduler/DuelScheduler.java
- ⚔️ 친구 대결 정산 (매일 00:30, 전날 끝난 대결 + 만료된 초대)
- 🤖 AI 주간 코치 (월요일 08:10)

### DuelService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/duel/service/DuelService.java
- ⚔️ 친구와 1:1 대결
- 흐름
- 1. 만들기 → 초대 링크 /duel/코드 (3일 안에 아무도 안 받으면 만료)
- 2. 친구가 링크를 열고 "대결 받기" → 그날부터 7일
- 3. 점수 = 🎯 예측 적중 ×10 + 📰 퀴즈 정답 ×5 + 📅 출석 하루 ×3 (기간 안의 실제 기록, 화면에서 실시간)
- 메서드: create(userId) · accept(code, userId) · cancel(code, userId) · view(code, viewerId) · mine(userId) · settleAll()
