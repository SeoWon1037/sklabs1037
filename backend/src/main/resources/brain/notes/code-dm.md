key: code-dm
title: Spring 코드 · dm 패키지
topics: spring, dm
---
## 요약
💬 1:1 메시지 (전부 로그인)

클래스 4개 · API 9개 · DB 표 4개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/dm`

## API
- GET /api/dm/stream → stream()
- GET /api/dm/unread → unread()
- GET /api/dm/users → users()
- GET /api/dm/rooms → rooms()
- POST /api/dm/rooms → open()
- GET /api/dm/rooms/{id} → messages()
- POST /api/dm/rooms/{id}/messages → send()
- POST /api/dm/rooms/{id}/read → read()
- POST /api/dm/rooms/{id}/block → block()

## DB 표
- T_DM_BLOCK, T_DM_MSG, T_DM_ROOM, T_USER

## 클래스

### DmController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/dm/controller/DmController.java
- 💬 1:1 메시지 (전부 로그인)

### DmDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/dm/dao/DmDao.java
- 💬 1:1 메시지 DAO (SQL: resources/mapper/DmMapper.xml)
- 방은 (작은 ID, 큰 ID) 한 쌍에 1개
- 메서드: searchUsers · selectUser · upsertRoom · selectRoom · selectRooms · countUnread · selectMessages · insertMessage · touchRoom · markRead · countSentToday · countBlockBetween · countBlocked · insertBlock · deleteBlock

### DmLiveHub · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/dm/service/DmLiveHub.java
- 💬 메시지 실시간 전달 (SSE, 토론방 BoardLiveHub와 같은 방식)
- 사람마다 연결 묶음 (휴대폰·PC 둘 다 열어도 둘 다 받음)
- 이벤트: msg { roomId, message } / read { roomId, readId }
- 연결이 하나도 없는 사람 = 메시지 화면을 안 보고 있음 → DmService가 📲 푸시로 대신 알림
- 25초마다 ping (끊긴 화면 정리)

### DmService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/dm/service/DmService.java
- 💬 1:1 메시지
- 아무 회원에게나 (닉네임으로 찾기) / 탈퇴·AI 계정에는 안 됨
- 실시간: DmLiveHub(SSE) → 메시지 화면을 열어둔 사람에게 바로
- 화면을 안 보고 있으면 📲 푸시 (같은 방 알림은 하나로 바뀜, Topic = DM{방})
- 읽음: 방마다 각자 "어디까지 읽었는지(메시지 ID)" → 내 메시지 옆 "1"이 사라짐
- 메서드: search(me, q) · open(me, otherId) · rooms(me) · unread(me) · messages(me, roomId, before) · send(me, roomId, body) · read(me, roomId, msgId) · block(me, roomId, on)
