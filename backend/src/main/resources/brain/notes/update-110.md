key: update-110
title: 업데이트 110 · 세컨드 브레인 (메모 → AI 정리 → 그래프)
topics: spring, 업데이트, brain
links: code-brain, code-ai
---
## 요약
메모(RAW)를 모으고, AI가 주제별 정리 문서(WIKI)로 묶고, 그걸로 결과물(OUTPUT)을 쓰는 기능. 2026-11-19.

## 바뀐 스프링 코드
- 새 패키지 brain: BrainRules(순수 계산) · BrainService · BrainController(/api/brain/*) · BrainDao + BrainMapper.xml
- 표: T_BRAIN_NOTE(노트), T_BRAIN_LINK(노트 사이 선) — db/auto/2026-11-19-01-brain.sql
- AI 챗봇 도구 3개: save_memo · search_memos · organize_memos (AiChatServiceImpl.runTool)
- RateLimitGuard: /api/brain/organize 10분 10번

## 배운 점 · 설계
- AI 정리는 @Transactional을 걸지 않음 → AI 응답을 기다리는 동안 DB 연결을 붙잡지 않으려고
- 하루 한도는 "오늘 0시부터"가 아니라 "최근 24시간" → 서버 시간대와 한국 시간이 달라도 틀리지 않음
- AI가 단 출처 번호는 서버가 보여 준 메모 id 안에 있는 것만 남김 → 없는 메모를 근거로 지어낼 수 없음
- LIKE 검색은 % _ \ 를 이스케이프 (ESCAPE '\')

```java
// 강제 도구: AI는 정해진 모양(write_wiki)으로만 답함 → 서버가 BrainRules.wiki()로 검사
Map<String, Object> res = claudeApiClient.createMessage(SYSTEM, List.of(WIKI_TOOL), messages,
        Map.of("type", "tool", "name", "write_wiki"), 3000);
BrainRules.Wiki wiki = BrainRules.wiki(toolInput(res), allowed, q);
```
