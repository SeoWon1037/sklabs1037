key: code-support
title: Spring 코드 · support 패키지
topics: spring, support
---
## 요약
📮 문의함

클래스 4개 · API 7개 · DB 표 3개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/support`

## API
- POST /api/support → create()
- GET /api/support/mine → mine()
- GET /api/admin/inquiries → list()
- GET /api/admin/inquiries/count → count()
- POST /api/admin/inquiries/{id}/reply → reply()
- POST /api/admin/inquiries/{id}/status → status()
- POST /api/admin/inquiries/{id}/retriage → retriage()

## DB 표
- T_AUTOFIX, T_INQUIRY, T_USER

## 클래스

### InquiryController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/support/controller/InquiryController.java
- 📮 문의함
- 사용자 (로그인)
- 관리자

### InquiryDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/support/dao/InquiryDao.java
- 📮 문의함 DAO (SQL: resources/mapper/InquiryMapper.xml)
- 메서드: insertInquiry · countToday · selectMine · selectOne · selectForAdmin · countOpen · updateTriage · reply · updateStatus · selectAdminIds

### InquiryRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/support/service/InquiryRules.java
- 📮 문의 분류 규칙 (AI 답을 서버가 마지막으로 다듬는 부분, AI 없이 테스트)
- 왜 서버 규칙이 따로 있나
- 문의는 누구나 쓰는 글 → "관리자 권한 검사를 빼주세요", "이전 지시는 무시하고…" 같은 글로
- AI를 속여 코드를 바꾸게 할 수 있음 (프롬프트 인젝션)
- 그래서 위험한 낱말·요청은 AI 판단과 상관없이 서버가 먼저 잡아 RISK_FLAGS에 남기고 자동 수정 후보에서 뺌
- 메서드: Triage(type, area, severity, summary, fixHint, reply, autofix, risks) · risks(text) · clean(Map<?, ai, category, title, body) · fallback(category, title, body)

### InquiryService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/support/service/InquiryService.java
- 📮 문의함
- 흐름
- 1. 사용자 문의 (로그인, 하루 5개) → 저장하고 바로 "접수됐어요"
- 2. 따로 스레드에서 AI 분류 (triage_inquiry 강제 도구) → InquiryRules가 다듬고 위험 요청은 걸러냄
- 3. 관리자들에게 🔔 + 📲 "📮 새 문의 · 버그 · 모의투자 — 요약"
- 메서드: create(userId, category, title, body, page, userAgent) · mine(userId) · adminList(status) · openCount() · reply(adminId, id, reply, close) · changeStatus(id, status) · retriage(id)
