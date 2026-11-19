key: update-111
title: 업데이트 111 · AI 보류 중 (긴급 정지 안내)
topics: spring, 업데이트, ai, ops
links: code-ai, code-ops, code-client
---
## 요약
Claude API가 401(키 거절)을 내서, 관리자 긴급 정지 때 모든 AI 기능이 "이 기능은 현재 보류 중입니다."라고 답하게 함. 2026-11-19.

## 바뀐 스프링 코드
- AiUsageService.PAUSED_MESSAGE · paused()
- AiChatController: GET /api/ai/status → { paused, message } (화면이 입력을 잠금)
- POST /api/ai/chat 에서 AiBudgetException → 503 + 이유 (예전엔 "AI 처리 중 오류")

## 배운 점 · 설계
- 401은 코드 버그가 아니라 키 문제 → 고칠 곳은 EC2 .env 와 GitHub Secrets
- docker compose restart 는 .env를 다시 읽지 않음 → up -d 로 다시 만들어야 함
- 막는 곳은 한 군데(ClaudeApiClient 앞의 check()) → 모든 AI 기능이 같이 멈추고, Claude를 아예 부르지 않아서 실패 메일도 멈춤

```java
// catch 순서: 더 구체적인 예외를 먼저
} catch (AiBudgetException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("success", false, "paused", true, "message", e.getMessage()));
} catch (RestClientResponseException e) {
```
