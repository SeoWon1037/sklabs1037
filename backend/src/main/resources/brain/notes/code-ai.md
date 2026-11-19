key: code-ai
title: Spring 코드 · ai 패키지
topics: spring, ai
---
## 요약
AI 챗봇 Controller

클래스 14개 · API 7개 · DB 표 6개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/ai`

## API
- GET /api/ai/status → status()
- POST /api/ai/chat → chat()
- POST /api/ai/feedback → feedback()
- GET /api/admin/ai-improve → overview()
- POST /api/admin/ai-improve/run → run()
- POST /api/admin/ai-improve/{version}/activate → activate()
- POST /api/admin/ai-improve/{version}/reject → reject()

## DB 표
- T_AI_CHAT_LOG, T_AI_PROMPT, T_NEWS, T_STOCK, T_STOCK_DOC, T_STOCK_PRICE

## 클래스

### AiChatController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/controller/AiChatController.java
- AI 챗봇 Controller
- 비용 보호
- 로그인하지 않아도 질문 가능: IP당 하루 20번 (AiChatRules.ANON_DAILY) + 요청 제한(RateLimitGuard) + 하루 비용 한도(ClaudeApiClient)

### AiImproveAdminController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/controller/AiImproveAdminController.java
- 관리자: 🔁 스스로 고치는 AI 챗봇

### AiChatDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/dao/AiChatDao.java
- AI 챗봇 Dao
- 메서드 이름 = AiChatMapper.xml의 id
- 메서드: selectStocks · selectPriceHistory · searchDocs · selectReturnRanking · searchNews · selectScreen · insertChatLog · updateRating

### AiPromptDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/dao/AiPromptDao.java
- 🔁 AI 챗봇 프롬프트 버전 DAO (SQL: resources/mapper/AiPromptMapper.xml)
- 메서드: selectActive · selectByVersion · selectMaxVersion · selectPrompts · selectLastRunAt · insertPrompt · retireActive · updateStatus · selectDownChats · selectUpChats · selectVersionStats

### PromptImproveScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/scheduler/PromptImproveScheduler.java
- 🔁 AI 챗봇 프롬프트 개선 스케줄러 (매일 04:10, 사람이 거의 안 쓰는 시간)
- 새 👎가 3개 이상일 때만 실제로 AI를 부름 (적으면 바로 끝)
- 작업 내용: PromptImproveService.run / 기록·실패 메일·로컬 PC에서 끄기: OpsJobs → JobRunService

### AiChatRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/service/AiChatRules.java
- 🤖 AI 챗봇의 고정 규칙 (순수 계산 → 테스트 가능)
- 시스템 프롬프트 = [틀] + [주식 규칙]
- 틀(FRAME)       : 무엇이든 답하기 · 로그인 안내 · 하트 보내기. 서버에 고정 (🔁 프롬프트 개선이 바꿀 수 없음)
- 주식 규칙        : AiChatServiceImpl.BASE_PROMPT 또는 관리자가 승인한 개선 버전 (주식 질문일 때만 적용)
- 로그인
- 메서드: openPage(page) · website(site, query) · programs(raw) · programsPrompt(programs) · programsPrompt(programs, helperOff) · launch(name, programs) · history(raw) · systemPrompt(stockRules, loggedIn, today) · loginRequired(tool) · needsLogin(tool, userId) · withoutActions(List<Map<String, tools) · allowAnonymous(Map<String, counts, day, ip)

### AiChatServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/service/AiChatServiceImpl.java
- AI 챗봇 Service 구현 (AI Agent)
- Agent 동작 방식
- 1. Claude에게 질문 + 쓸 수 있는 도구 목록을 보냄
- 2. Claude가 "이 도구 써줘(tool_use)"라고 하면 → 우리가 실행해서 결과를 돌려줌
- 3. Claude가 최종 답변(end_turn)을 줄 때까지 반복 (최대 MAX_TURNS번)
- 메서드: chat(vo) · answerWith(systemPrompt, question, userId) · saveRating(vo)

### IAiChatService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/service/IAiChatService.java
- AI 챗봇 Service

### PromptImproveService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/service/PromptImproveService.java
- 🔁 스스로 고치는 AI 챗봇
- 매일 04:10 (PromptImproveScheduler → OpsJobs "AI_IMPROVE"), 또는 관리자 "지금 실행"
- 1. 재료 모으기 : 지난번 개선 이후 👎 받은 대화 (3개 미만이면 쉼)
- 2. 분석·개선안 : Claude가 실패 패턴을 찾고 시스템 프롬프트를 고침 (propose_prompt 도구)
- → 서버 확인: 안전 규칙 문구가 남아 있는지, 길이, 실제로 바뀌었는지
- 메서드: run() · overview() · activate(version, adminId) · reject(version, adminId)

### PromptStore · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/service/PromptStore.java
- 🔁 AI 챗봇이 지금 쓰는 시스템 프롬프트
- 관리자가 적용(ACTIVE)한 개선 버전이 있으면 그것, 없으면 코드의 기본 프롬프트(버전 0)
- 매 질문마다 DB를 읽지 않게 1분 캐시 (적용·되돌리기 하면 바로 비움)

### AiChatVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/vo/AiChatVO.java
- AI 챗봇 화면의 요청/응답 VO
- 사용 테이블: T_AI_CHAT_LOG (대화 기록 + 피드백)

### AiPromptVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/vo/AiPromptVO.java
- 🔁 AI 챗봇 프롬프트 버전 / 개선 재료
- 사용 테이블: T_AI_PROMPT, T_AI_CHAT_LOG

### AiScreenVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/vo/AiScreenVO.java
- AI 종목 스크리너(screen_stocks 도구) VO
- AI가 SQL을 쓰는 게 아니라, 정해진 조건 칸만 채우고 SQL은 서버(AIChatMapper.selectScreen)가 만듦
- → 아무 SQL이나 실행될 위험 없음, 숫자는 전부 범위를 잘라서 넣음
- 사용 테이블: T_STOCK, T_STOCK_PRICE

### AiStockVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ai/vo/AiStockVO.java
- AI 챗봇의 도구(tool)가 DB를 조회할 때 쓰는 VO
- 사용 테이블: T_STOCK, T_STOCK_PRICE, T_STOCK_DOC, T_NEWS
