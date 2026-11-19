key: code-demo
title: Spring 코드 · demo 패키지
topics: spring, demo
---
## 요약
🧪 로그인 없이 체험하기

클래스 5개 · API 1개 · DB 표 3개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/demo`

## API
- POST /api/user/demo → demo()

## DB 표
- T_NOTIFICATION, T_PRICE_ALERT, T_USER

## 클래스

### DemoGuard · Config
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/demo/config/DemoGuard.java
- 요청 앞에서)
- 체험 세션(DEMO = true)이 허용 목록에 없는 쓰기 요청을 보내면 403 + 안내 문구
- 일반 회원 · 로그인 안 한 사람은 그대로 통과 (아무 영향 없음)

### DemoController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/demo/controller/DemoController.java
- 🧪 로그인 없이 체험하기

### DemoDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/demo/dao/DemoDao.java
- 🧪 체험 계정 DAO (SQL: resources/mapper/DemoMapper.xml)
- 메서드: selectDemoUser · deleteNotifications · deletePriceAlerts

### DemoResetScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/demo/scheduler/DemoResetScheduler.java
- 🧪 체험 계정 매일 초기화 (05:10)
- 작업 내용: DemoService.resetDaily / 기록·실패 메일: OpsJobs → JobRunService

### DemoService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/demo/service/DemoService.java
- 🧪 체험 계정 (로그인 없이 체험하기)
- 모두가 같은 계정(T_USER.PROVIDER = 'DEMO') 하나로 들어옴 → 회원가입 없이 핵심 기능을 바로 써 봄
- 세션에 DEMO = true 표시 → DemoGuard가 쓰기 요청을 허용 목록(ALLOWED)만 통과시킴
- (토론 글 · 메시지 · 문의 · 커플 · 닉네임·비밀번호 · 탈퇴 · 푸시 구독 같은 건 막음 → 남에게 보이는 흔적·악용 방지)
- AI 하루 사용 횟수도 이 계정 하나로 같이 셈 → 체험이 AI 비용을 크게 늘리지 않음 (+ 💸 AI 비용 한도)
- 메서드: loginUser() · resetDaily() · allowed(method, uri)
