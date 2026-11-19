key: code-brain
title: Spring 코드 · brain 패키지
topics: spring, brain
---
## 요약
🧠 세컨드 브레인 Controller (모두 로그인 필요, 본인 노트만)

클래스 6개 · API 10개 · DB 표 3개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/brain`

## API
- GET /api/brain/notes → list()
- GET /api/brain/notes/{id} → get()
- POST /api/brain/notes → create()
- POST /api/brain/notes/{id} → update()
- POST /api/brain/notes/{id}/delete → delete()
- GET /api/brain/graph → graph()
- POST /api/brain/organize → organize()
- POST /api/brain/sample → sample()
- GET /api/brain/import → importStatus()
- POST /api/brain/import → importNotes()

## DB 표
- T_BRAIN_LINK, T_BRAIN_NOTE, T_BRAIN_SEEN

## 클래스

### BrainController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brain/controller/BrainController.java
- 🧠 세컨드 브레인 Controller (모두 로그인 필요, 본인 노트만)

### BrainDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brain/dao/BrainDao.java
- 🧠 세컨드 브레인 DAO (SQL: resources/mapper/BrainMapper.xml) — 모든 조회 · 수정에 USER_ID 조건 (본인 것만)
- 메서드: countNotes · selectNotes · selectNote · insertNote · updateNote · deleteNote · insertLink · deleteLinksOf · selectLinkedTo · selectLinkedFrom · selectGraphNotes · selectLinks · searchRaw · countWikiToday · selectSourceNote · countSeen · insertSeen · insertSourceNote · updateSourceNote · selectImportUsers · countImported

### BrainImportRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brain/service/BrainImportRules.java
- 📥 사이트가 넣어 주는 노트 파일(resources/brain/notes/*.md) 읽기 — 순수 계산 (테스트: BrainImportRulesTest)
- 파일 모양 (맨 위 머리 + --- + 본문):
- key: code-brain
- title: Spring 코드 · brain 패키지
- topics: spring, brain
- 메서드: Note(key, kind, title, topics, links, body) · parse(fileName, text) · hash(body) · decide(seen, storedHash, currentBodyHash, newHash)

### BrainImportService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brain/service/BrainImportService.java
- 📥 SKLabs 스프링 코드 기록 → 내 세컨드 브레인
- 노트 파일: resources/brain/notes/*.md (번들을 만들 때마다 같이 갱신)
- code-○○   : 패키지별 지금 코드 요약 (tools/brain/gen-code-notes.mjs가 자바 파일에서 만듦)
- update-○○ : 업데이트마다 바뀐 스프링 코드 설명
- 관리자가 /brain에서 [받기]를 한 번 누르면, 그 뒤로는 서버가 켜질 때(= 배포 때마다) 새 기록을 자동으로 넣음
- 메서드: notes() · sync(userId) · status(userId) · onReady()

### BrainRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brain/service/BrainRules.java
- 🧠 세컨드 브레인 규칙 (순수 계산 → 테스트 가능)
- 흐름: 모으기(RAW 메모) → 정리하기(AI가 주제별 WIKI 문서로) → 꺼내 쓰기(OUTPUT) — 주제 태그가 그래프의 허브
- 원칙: "AI는 정리, 판단은 서버 규칙"
- AI에게는 고른 메모만 보여 주고, AI가 답한 출처 번호는 그 안에 있는 것만 남김 (없는 메모를 지어내 근거로 달 수 없음)
- 정리 문서의 모양(제목 · 요약 · 소제목 · 항목 수 · 길이)은 서버가 자르고 맞춤
- 메서드: kind(raw) · kindFilter(raw) · body(raw) · title(raw, body) · topics(explicit, body) · topicsColumn(topics) · splitTopics(column) · query(raw) · likeEscape(q) · snippet(body) · material(List<Map<String, notes) · Wiki(title, body, topics, sourceIds) · wiki(Map<?, input, allowedIds, query) · graph(List<Map<String, notes, List<Map<String, links)

### BrainService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/brain/service/BrainService.java
- 🧠 세컨드 브레인
- 모으기   : 메모(RAW) 남기기 — 화면(/brain) 또는 AI 챗봇("메모해줘: …" → save_memo 도구). #태그가 주제가 됨
- 정리하기 : "Spring 정리해줘" → 그 말이 들어간 RAW 메모를 최근 것부터 30개 골라 Claude에게 보여 주고(write_wiki 강제 도구)
- 서버가 모양을 다듬어 WIKI 노트로 저장, 정리에 쓴 메모와 연결 (그래프의 선)
- 꺼내 쓰기: OUTPUT 노트(블로그 글 · 면접 답변 등)를 직접 남기고 바탕이 된 WIKI와 연결
- 메서드: list(userId, kind, q) · get(userId, id) · create(userId, kind, title, body, topics, fromId) · update(userId, id, title, body, topics) · delete(userId, id) · graph(userId) · search(userId, q) · sample(userId) · organize(userId, rawQuery)
