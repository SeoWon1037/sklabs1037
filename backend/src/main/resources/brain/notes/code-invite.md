key: code-invite
title: Spring 코드 · invite 패키지
topics: spring, invite
---
## 요약
🤝 친구 초대 (로그인 필요)

클래스 4개 · API 2개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/invite`

## API
- GET /api/invite → my()
- POST /api/invite/accept → accept()

## DB 표
- T_INVITE, T_USER

## 클래스

### InviteController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/invite/controller/InviteController.java
- 🤝 친구 초대 (로그인 필요)

### InviteDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/invite/dao/InviteDao.java
- 🤝 친구 초대 DAO (SQL: resources/mapper/InviteMapper.xml)
- 메서드: selectUser · insertInvite · countInvited · selectInvited · selectInviterName

### InviteRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/invite/service/InviteRules.java
- 🤝 친구 초대 규칙 (순수 계산)
- 초대 코드 = (회원 번호 × 7919 + 1037)을 36진수로 → 따로 저장하지 않아도 코드 ↔ 회원 번호를 오갈 수 있음
- (아무 글자나 넣으면 대부분 맞지 않아서 걸러짐. 비밀은 아님 — 초대는 배지만 주니까)
- 초대는 "가입한 지 3일 안"인 사람만 받을 수 있음 (오래된 회원끼리 서로 눌러 배지를 받는 것 방지)
- 메서드: code(userId) · userId(raw) · refuse(inviterId, me, myProvider, joinedAt, now)

### InviteService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/invite/service/InviteService.java
- 🤝 친구 초대 — 내 링크(/?ref=코드)로 들어와 가입하면 두 사람 모두 배지
- 흐름: 링크로 들어옴 → 브라우저가 코드를 기억 → 가입 · 로그인(소셜 포함)한 뒤 화면이 코드를 서버로 보냄 (accept)
- → 가입 화면 · 소셜 로그인 코드를 건드리지 않고 어떤 가입 방법에서도 됨
- 받을 수 있는 사람: 가입한 지 3일 안, 아직 초대받은 적 없음, 체험 계정 아님 (InviteRules.refuse)
- 보상은 배지만 (가상머니를 주면 모의투자 수익률 랭킹이 틀어짐)
- 메서드: my(me) · accept(me, code)
