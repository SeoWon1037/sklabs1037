key: code-admin
title: Spring 코드 · admin 패키지
topics: spring, admin
---
## 요약
관리자 피드백 대시보드 Controller

클래스 5개 · API 3개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/admin`

## API
- GET /api/admin/check → check()
- GET /api/admin/feedback → dashboard()
- GET /api/admin/feedback/chats → chats()

## DB 표
- T_AI_CHAT_LOG, T_USER

## 클래스

### AdminFeedbackController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/admin/controller/AdminFeedbackController.java
- 관리자 피드백 대시보드 Controller
- 로그인 + T_USER.ROLE = 'ADMIN' 인 회원만 (매 요청마다 DB에서 확인)

### AdminFeedbackDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/admin/dao/AdminFeedbackDao.java
- 관리자 피드백 대시보드 Dao
- 메서드 이름 = AdminFeedbackMapper.xml의 id
- 메서드: selectUserRole · selectSummary · selectToolUsage · selectDailyStats · selectChats

### AdminFeedbackServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/admin/service/AdminFeedbackServiceImpl.java
- 관리자 피드백 대시보드 Service 구현
- 메서드: isAdmin(userId) · getDashboard() · getChats(vo)

### IAdminFeedbackService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/admin/service/IAdminFeedbackService.java
- 관리자 피드백 대시보드 Service

### AdminFeedbackVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/admin/vo/AdminFeedbackVO.java
- 관리자 피드백 대시보드 VO
- 사용 테이블: T_AI_CHAT_LOG, T_USER
