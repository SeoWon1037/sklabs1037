key: code-league
title: Spring 코드 · league 패키지
topics: spring, league
---
## 요약
🤖 AI vs 사람 투자 리그

클래스 6개 · API 1개 · DB 표 6개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/league`

## API
- GET /api/league → league()

## DB 표
- T_AI_TRADER, T_AI_TRADE_LOG, T_NEWS, T_STOCK, T_STOCK_PRICE, T_USER

## 클래스

### AiLeagueController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/league/controller/AiLeagueController.java
- 🤖 AI vs 사람 투자 리그
- 매매 실행은 스케줄러(평일 09:10) 또는 관리자 운영 화면 "지금 실행" (작업 이름 AI_LEAGUE)

### AiLeagueDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/league/dao/AiLeagueDao.java
- AI 투자 리그 DAO (SQL: resources/mapper/AiLeagueMapper.xml)
- 메서드: selectTraders · countLog · insertLog · selectRecentLogs · selectUniverse · selectStockNews · selectMarketNews

### AiLeagueScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/league/scheduler/AiLeagueScheduler.java
- AI 투자 리그 스케줄러 (평일 09:10, 장 시작 10분 뒤 → 시초가 출렁임을 조금 피해서)
- 작업 내용: AiLeagueService.runAll / 실행 기록·실패 메일·로컬 PC에서 끄기: OpsJobs → JobRunService

### AiLeagueService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/league/service/AiLeagueService.java
- 🤖 AI vs 사람 투자 리그
- 평일 09:10 (AiLeagueScheduler → OpsJobs "AI_LEAGUE")
- AI 투자자마다
- 1. 내 계좌(현금·보유 종목) + 후보 종목 60개(시총 상위, 5일·20일 수익률, 변동성) + 최근 뉴스 제목을 모아서
- 2. Claude에게 성격(AiTraderStyle.persona)대로 판단하게 함 → decide_trades 도구로 정해진 모양의 주문만 받음
- 메서드: runAll() · getLeague()

### AiTraderStyle · enum
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/league/service/AiTraderStyle.java
- AI 투자자 성격 (T_AI_TRADER.CODE)
- persona   : AI에게 주는 투자 철학 (프롬프트)
- 숫자 규칙 : AI가 뭐라고 하든 서버가 지킴 (종목 수·한 종목 최대 비중·최소 현금·하루 주문 수)
- → "AI는 판단, 규칙은 서버" (AI가 몰빵하거나 현금을 다 쓰지 못하게)

### AiLeagueVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/league/vo/AiLeagueVO.java
- AI 투자 리그 VO
- 사용 테이블: T_AI_TRADER, T_AI_TRADE_LOG, T_USER, T_STOCK, T_STOCK_PRICE, T_NEWS
