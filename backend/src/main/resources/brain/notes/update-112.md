key: update-112
title: 업데이트 112 · 스프링 코드 기록을 세컨드 브레인에 자동 저장
topics: spring, 업데이트, brain, ops
links: code-brain, code-overview
---
## 요약
SKLabs의 지금 스프링 코드(패키지별 요약)와 업데이트마다 바뀐 점을 관리자 세컨드 브레인에 넣고, 배포할 때마다 자동으로 새 기록을 더함. 2026-11-19.

## 바뀐 스프링 코드
- brain/service/BrainImportRules(순수): 노트 파일 읽기(key · title · topics · links + 본문), MD5 지문, 할 일 정하기(NEW · UPDATE · KEEP)
- brain/service/BrainImportService: classpath*:brain/notes/*.md 읽기 → sync(userId), @EventListener(ApplicationReadyEvent) 로 배포 때마다 자동
- BrainController: GET · POST /api/brain/import (관리자만)
- 표: T_BRAIN_NOTE.SOURCE_KEY · SOURCE_HASH, T_BRAIN_SEEN — db/auto/2026-11-19-02-brain-import.sql
- AutoSqlRunner에 @Order(HIGHEST_PRECEDENCE) → 표를 먼저 만들고 나서 다른 시작 작업
- tools/brain/gen-code-notes.mjs: 자바 파일을 읽어 code-○○.md 만들기

## 배운 점 · 설계
- 내가 고친 노트는 덮어쓰지 않음: DB의 MD5(BODY)가 넣을 때 지문(SOURCE_HASH)과 같을 때만 UPDATE
- 내가 지운 노트는 다시 넣지 않음: 받은 이름표를 T_BRAIN_SEEN에 따로 기록 (노트가 없어도 "받은 적 있음")
- 부분 유니크 인덱스: (USER_ID, SOURCE_KEY) WHERE SOURCE_KEY IS NOT NULL → 내가 쓴 메모(SOURCE_KEY 없음)는 제한 없음
- 같은 ApplicationReadyEvent를 듣는 리스너끼리는 순서가 정해져 있지 않음 → @Order로 정함

```sql
UPDATE T_BRAIN_NOTE
   SET BODY = #{body}, SOURCE_HASH = #{hash}, UPDATED_AT = NOW()
 WHERE USER_ID = #{userId} AND ID = #{id}
   AND MD5(BODY) = SOURCE_HASH   -- 사람이 고쳤으면 0줄
```
