key: code-calendar
title: Spring 코드 · calendar 패키지
topics: spring, calendar
---
## 요약
📅 증시 일정 (로그인 없이 누구나)

클래스 5개 · API 3개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/calendar`

## API
- GET /api/market/calendar → calendar()
- GET /api/market/calendar/alert → getAlert()
- POST /api/market/calendar/alert → setAlert()

## DB 표
- T_CALENDAR_ALERT, T_USER

## 클래스

### MarketCalendarController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/calendar/controller/MarketCalendarController.java
- 📅 증시 일정 (로그인 없이 누구나)

### CalendarAlertDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/calendar/dao/CalendarAlertDao.java
- 🔔 증시 일정 알림 DAO (SQL: resources/mapper/CalendarAlertMapper.xml)
- 메서드: selectKinds · upsert · delete · selectAll

### CalendarAlertScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/calendar/scheduler/CalendarAlertScheduler.java
- 🔔 증시 일정 알림 스케줄러 (매일 19:00, 내일 일정을 전날 저녁에)
- 작업 내용: CalendarAlertService.sendAll / 기록·실패 메일: OpsJobs → JobRunService

### CalendarAlertService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/calendar/service/CalendarAlertService.java
- 🔔 증시 일정 알림 (작업 CALENDAR_ALERT, 매일 19:00)
- 내일 일정(MarketCalendar)이 있으면, 그 종류를 켠 회원에게 사이트 알림 + 📲 푸시 (한 사람에 하루 1개로 묶어서)
- FOMC는 한국 시간 새벽이라 "내일 새벽 4시"처럼 전날 저녁에 알려주는 게 맞음
- 같은 날 두 번 돌아도 알림 중복 방지 키(CAL:날짜)로 한 번만
- 메서드: Message(title, body) · get(userId) · set(userId, enabled, kinds) · sendAll()

### MarketCalendar · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/calendar/service/MarketCalendar.java
- 📅 증시 일정 (DB 없이 코드 안의 표 + 규칙, 순수 계산이라 테스트 가능)
- 1. 정해진 날 (아래 표, 발표되면 여기에 추가)
- 한국거래소 휴장일 · 첫 거래일 늦은 개장
- 🇺🇸 FOMC 금리 결정: 미국 동부 오후 2시 발표 → 한국 시간으로 바꿔서 (다음 날 새벽 3시 또는 4시)
- 🇰🇷 한국은행 금융통화위원회 기준금리 결정 (오전 9시대 발표)
