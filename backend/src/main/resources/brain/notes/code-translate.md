key: code-translate
title: Spring 코드 · translate 패키지
topics: spring, translate
---
## 요약
🌐 번역 (로그인 없이 누구나)

클래스 3개 · API 1개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/translate`

## API
- POST /api/ai/translate → translate()

## 클래스

### TranslateController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/translate/TranslateController.java
- 🌐 번역 (로그인 없이 누구나)
- target: ko · en · ja · zh · es · vi

### TranslateRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/translate/TranslateRules.java
- 🌐 번역 규칙 (순수 계산)
- 번역은 AI가 하지만, 무엇을 받고 무엇을 내보낼지는 서버가 정함 ("AI는 정리, 판단은 서버 규칙")
- 받는 글: 500자까지, 번역할 언어는 아래 목록에서만
- AI 답: 길이 제한, 줄바꿈 정리, 원문 언어는 목록에 있을 때만 (없으면 "기타")
- 메서드: text(raw) · target(raw) · result(Map<?, in, target)

### TranslateService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/translate/TranslateService.java
- 🌐 번역 — 말하거나 쓴 글을 고른 언어로 (로그인 없이도, 화면: /translate)
- AI에게는 번역만 시킴: 정해진 도구(translate_text)로만 답하게 하고, 받은 답은 TranslateRules.result로 다시 정리
- 번역할 글 안에 "이전 지시를 무시해" 같은 말이 있어도 그 말을 번역할 뿐, 따르지 않게 함 (글은 <text> 안의 자료)
- 비용: 한 사람(IP)이 하루 60번까지 + 요청 횟수 제한(RateLimitGuard) + 💸 AI 하루 한도(AiUsageService)
- 메서드: translate(rawText, rawTarget, who)
