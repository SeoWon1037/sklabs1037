key: code-idea
title: Spring 코드 · idea 패키지
topics: spring, idea
---
## 요약
💡 기능 제안 + 투표

클래스 3개 · API 6개 · DB 표 3개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/idea`

## API
- GET /api/ideas → list()
- POST /api/ideas/check → check()
- POST /api/ideas → create()
- POST /api/ideas/{id}/vote → vote()
- DELETE /api/ideas/{id} → delete()
- POST /api/ideas/{id}/status → status()

## DB 표
- T_IDEA, T_IDEA_VOTE, T_USER

## 클래스

### IdeaController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/idea/controller/IdeaController.java
- 💡 기능 제안 + 투표

### IdeaDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/idea/dao/IdeaDao.java
- 💡 기능 제안 + 투표 DAO (SQL: resources/mapper/IdeaMapper.xml)
- 메서드: selectIdeas · selectStatusCounts · selectCandidates · selectIdea · countToday · insertIdea · deleteIdea · insertVote · deleteVote · addVoteCount · selectVoteCount · updateStatus · selectVoters

### IdeaService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/idea/service/IdeaService.java
- 💡 기능 제안 + 투표
- 누구나 보고, 로그인하면 제안(하루 5개) · 👍 투표 (다시 누르면 취소, 내 제안은 자동 1표)
- 쓰기 전에 "비슷한 제안" 찾기: AI가 기존 제안 목록에서 같은 뜻을 고르고 카테고리를 붙임
- (AI가 없거나 실패하면 낱말이 겹치는 정도로) → 이미 있으면 거기에 투표하도록 안내
- 관리자: 상태 바꾸기 (제안 → 만드는 중 → 나왔어요 / 보류) + 한마디
- 메서드: list(userId, sort, status) · check(title, body) · create(userId, title, body, category) · delete(userId, id) · vote(userId, id) · changeStatus(id, status, note)
