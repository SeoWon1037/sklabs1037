key: code-brief
title: Spring 코드 · brief 패키지
topics: spring, brief
---
## 요약
아침 브리핑 메일 Controller (마이페이지)

클래스 5개 · API 3개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/brief`

## API
- GET /api/briefing → status()
- POST /api/briefing → setEnabled()
- POST /api/briefing/test → test()

## DB 표
- T_MORNING_BRIEF, T_USER

## 클래스

### MorningBriefController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brief/controller/MorningBriefController.java
- 아침 브리핑 메일 Controller (마이페이지)

### MorningBriefDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brief/dao/MorningBriefDao.java
- 아침 브리핑 메일 Dao
- 메서드 이름 = MorningBriefMapper.xml의 id
- 메서드: selectMyBrief · upsertEnabled · selectTargets · markSent · revertSent

### MorningBriefScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brief/scheduler/MorningBriefScheduler.java
- 아침 브리핑 메일 스케줄러 (평일 08:00)
- 작업 내용: MorningBriefService.sendAll / 실행 기록·실패 메일: OpsJobs → JobRunService

### MorningBriefService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brief/service/MorningBriefService.java
- 아침 브리핑 메일 Service
- 평일 08:00 (MorningBriefScheduler → OpsJobs "MORNING_BRIEF")
- 1. 오늘 아직 안 받은 신청자 목록
- 2. 한 명씩: 먼저 "오늘 보냄"으로 표시(markSent = 1일 때만) → 메일 → 실패하면 되돌림
- 3. 내용: 시장 AI 요약 + 관심 종목(어제 종가·등락, 주요 종목은 AI 뉴스 요약 한 줄) + 모의투자 계좌
- 메서드: getStatus(userId) · setEnabled(userId, enabled) · sendTest(userId) · sendAll() · calendarLines(today)

### MorningBriefVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brief/vo/MorningBriefVO.java
- 아침 브리핑 메일 VO
- 사용 테이블: T_MORNING_BRIEF, T_USER
