key: code-fx
title: Spring 코드 · fx 패키지
topics: spring, fx
---
## 요약
💱 오늘의 환율 (로그인 없이)

클래스 2개 · API 1개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/fx`

## API
- GET /api/fx → today()

## 클래스

### FxController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/fx/controller/FxController.java
- 💱 오늘의 환율 (로그인 없이)

### FxService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/fx/service/FxService.java
- 💱 오늘의 환율 (메인 "오늘의 환율" 탭)
- 달러 기준 한 번에 받아서 원화로 바꿈: 1 엔 = (달러→원) / (달러→엔)
- 엔은 100엔 기준 (은행 고시와 같게)
- 30분 캐시, 외부 API가 실패하면 마지막으로 받은 값을 그대로 (stale 표시)
- 메서드: today()
