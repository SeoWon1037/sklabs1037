key: code-quiz
title: Spring 코드 · quiz 패키지
topics: spring, quiz
---
## 요약
📰 오늘의 뉴스 퀴즈

클래스 4개 · API 2개 · DB 표 5개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/quiz`

## API
- GET /api/quiz → today()
- POST /api/quiz/submit → submit()

## DB 표
- T_NEWS, T_NEWS_QUIZ, T_NEWS_QUIZ_PLAY, T_STOCK, T_USER

## 클래스

### NewsQuizController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/quiz/controller/NewsQuizController.java
- 📰 오늘의 뉴스 퀴즈

### NewsQuizDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/quiz/dao/NewsQuizDao.java
- 📰 오늘의 뉴스 퀴즈 DAO (SQL: resources/mapper/NewsQuizMapper.xml)
- 결과는 Map (키는 SQL에서 "camelCase" 별칭)
- 메서드: selectRecentNews · insertQuiz · selectLatestQuiz · countQuiz · selectPlay · insertPlay · selectDayStats · selectScoreCounts · selectWeekRanking · selectMyDates

### NewsQuizScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/quiz/scheduler/NewsQuizScheduler.java
- 📰 오늘의 뉴스 퀴즈 출제 (매일 07:40, 07:30 AI 뉴스 요약 직후)

### NewsQuizService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/quiz/service/NewsQuizService.java
- 📰 오늘의 뉴스 퀴즈
- 출제 (매일 07:40, NewsQuizScheduler → OpsJobs "NEWS_QUIZ")
- 1. 최근 30시간 기사 제목 최대 40개 (스포츠 제외, 같은 제목 한 번) + 번호
- 2. Claude가 make_quiz 도구로 5문제: 질문·보기 4개·정답·해설·근거 기사 번호
- 제목에 나온 사실만으로 풀 수 있게, 예측·투자 판단 문제 금지
- 메서드: generateToday() · today(userId) · submit(userId, answers)
