key: code-me
title: Spring 코드 · me 패키지
topics: spring, me
---
## 요약
내 활동 · 알림 · 오늘의 SKLabs (모두 로그인)

클래스 10개 · API 9개 · DB 표 19개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/me`

## API
- GET /api/me/coach → coach()
- POST /api/me/coach → createCoach()
- POST /api/me/share → share()
- GET /api/me/activity → activity()
- GET /api/me/today → today()
- GET /api/me/notifications → notifications()
- GET /api/me/notifications/unread → unread()
- POST /api/me/notifications/read → read()
- GET /api/share/{code} → get()

## DB 표
- T_AI_CHAT_LOG, T_ATTENDANCE, T_BEAUTY_LOG, T_MOCK_TRADE, T_NEWS_QUIZ, T_NEWS_QUIZ_PLAY, T_NOTIFICATION, T_PERSONA_RESULT, T_PREDICT_ITEM, T_PREDICT_VOTE, T_PRICE_ALERT, T_SHARE_CARD, T_STOCK_COMMENT, T_STOCK_POST, T_STOCK_POST_LIKE, T_USER, T_USER_BADGE, T_WATCHLIST, T_WEEKLY_COACH

## 클래스

### MeController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/controller/MeController.java
- 내 활동 · 알림 · 오늘의 SKLabs (모두 로그인)

### ShareController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/controller/ShareController.java
- 📸 공유 카드 보기 (로그인 없이, 링크를 받은 누구나)

### MeDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/dao/MeDao.java
- 🏅 내 활동·배지 / 🔔 알림 / 📅 출석 DAO (SQL: resources/mapper/MeMapper.xml)
- 결과는 Map (키는 SQL의 "camelCase" 별칭)
- 메서드: selectActivity · selectBadges · insertBadge · insertNotification · insertPredictNotifications · selectNotifications · countUnread · markRead · insertAttendance · selectAttendDays · countPredictVotes · countPredictItems · countBoardPostsToday · selectRangeStats · selectActiveUsers · selectNickname · selectCoach · insertCoach · insertShare · selectShare · countSharesToday

### ActivityService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/ActivityService.java
- 🏅 내 활동 / 📅 오늘의 SKLabs
- activity(): 여러 기능에 흩어진 내 기록을 한 화면으로
- 모의투자(거래 수·수익률) · 예측(적중률·최고 연속) · 퀴즈(참여·만점·최고 연속) · 토론(글·좋아요·댓글)
- 탐험(투자 성향·AI 대화·성분 분석·관심 종목·알림) · 출석 → 배지 판정(BadgeService)
- today(): 로그인하고 메인을 열면 출석 도장 + 오늘 할 일 체크리스트
- 메서드: activity(userId) · today(userId)

### BadgeService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/BadgeService.java
- 🏅 업적·배지
- 조건은 여기(코드)에, 받은 날짜는 T_USER_BADGE 에
- "내 활동"을 열 때마다 지금 숫자로 다시 확인해서 새로 달성한 배지를 주고 🔔 알림
- (따로 이벤트를 심지 않아도 예측·퀴즈·모의투자·토론 어디서 달성하든 다음에 열면 받음)
- 메서드: Stats(tradeCount, returnRate, beatAi, predictVotes, predictGraded, predictHits, predictBestStreak, quizPlays, quizPerfect, quizBestStreak, boardPosts, likesReceived, comments, aiChats, beautyLogs, watchCount, alertCount, personaDone, attendDays, attendBestStreak) · Badge(code, icon, name, desc, group, rule) · award(userId, stats) · awardOne(userId, code) · find(code)

### CoachService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/CoachService.java
- 🤖 AI 주간 코치
- 흐름
- 1. 매주 월요일 08:10 (WEEKLY_COACH): 지난주에 들어온 사람마다 지난주·지지난주 숫자를 모아
- 2. Claude가 write_coach 도구로 한 줄 제목 · 돌아보기 · 잘한 점 · 이번 주 팁 · 미션 3개(종류·목표 숫자·이유)
- 3. 서버가 확인: 미션 종류는 정해진 9개 중에서만, 목표 숫자는 종류별 범위로 자름, 모자라면 기본 미션으로 채움
- 메서드: weekStart(day) · view(userId) · create(userId) · runWeekly()

### Codes · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/Codes.java
- 공유 링크용 짧은 코드 (📸 공유 카드 /s/코드, ⚔️ 대결 /duel/코드)
- 헷갈리는 글자(0 O 1 l I)는 뺌, 10자면 추측으로 찾기 어려움

### NotificationService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/NotificationService.java
- 🔔 사이트 안 알림
- 보내는 곳
- BadgeService        : 새 배지 (BADGE)
- PredictService      : 예측 채점 끝 → 그 날 투표한 사람마다 "n문제 중 m개 적중" (PREDICT)
- StockBoardService   : 내 토론 글에 댓글 (COMMENT), 신고가 쌓여 내 글이 가려짐 (REPORT)
- 메서드: notify(userId, kind, title, body, link) · notify(userId, kind, title, body, link, dedupeKey) · predictGraded(tradeDate) · list(userId) · unread(userId) · markRead(userId, id)

### RangeStats · record
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/RangeStats.java
- 기간 숫자 (🤖 주간 코치 · ⚔️ 친구 대결 · 📸 공유 카드)
- MeMapper.selectRangeStats 결과를 숫자로

### ShareService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/me/service/ShareService.java
- 📸 내 성적 공유 카드
- "카드 만들기"를 누른 순간의 숫자를 저장하고 짧은 코드를 줌 → https://sklabs1037.com/s/코드
- 링크를 연 사람은 저장된 스냅샷만 봄 (닉네임 · 이번 주 예측·퀴즈·출석 · 모의투자 수익률 · 배지)
- 이메일·계정 정보는 넣지 않음
- 하루 20장까지
- 메서드: create(userId) · get(code)
