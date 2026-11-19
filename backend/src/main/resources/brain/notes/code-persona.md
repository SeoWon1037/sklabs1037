key: code-persona
title: Spring 코드 · persona 패키지
topics: spring, persona
---
## 요약
🧭 투자 성향 테스트

클래스 2개 · API 2개 · DB 표 1개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/persona`

## API
- POST /api/persona/result → save()
- GET /api/persona/stats → getStats()

## DB 표
- T_PERSONA_RESULT

## 클래스

### PersonaController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/persona/controller/PersonaController.java
- 🧭 투자 성향 테스트
- 유형 계산은 화면(질문·점수표)에서, 서버는 값이 말이 되는지만 확인
- (축 점수 범위, 유형 코드가 점수와 맞는지) → 통계가 엉뚱한 값으로 오염되지 않게

### PersonaDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/persona/dao/PersonaDao.java
- 🧭 투자 성향 테스트 DAO (SQL: resources/mapper/PersonaMapper.xml)
- 메서드: insertResult · selectTypeCounts
