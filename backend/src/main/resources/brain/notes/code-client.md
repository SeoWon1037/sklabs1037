key: code-client
title: Spring 코드 · client 패키지
topics: spring, client
---
## 요약
💸 AI 하루 한도를 다 썼거나 관리자가 긴급 정지 → Claude를 부르지 않음

클래스 9개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/client`

## 클래스

### AiBudgetException · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/AiBudgetException.java
- 💸 AI 하루 한도를 다 썼거나 관리자가 긴급 정지 → Claude를 부르지 않음
- IllegalStateException이라 기존 기능들은 이 메시지를 그대로 사용자에게 보여줌
- 작업(스케줄)은 실패 메일 대신 "건너뜀"으로 기록 (JobRunService)

### ApiCallLog · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/ApiCallLog.java
- 🔌 외부 연동 기록장 (메모리)
- 외부 서비스를 부를 때마다 record()로 한 줄 → 큐에 쌓아 두기만 함 (DB에 바로 안 씀)
- → ApiCallLogService가 10초마다 drain()으로 꺼내 한꺼번에 저장
- 왜 바로 DB에 안 쓰나
- 저녁 시세 수집은 토스를 수천 번 부름 → 그때마다 INSERT하면 수집이 느려지고 DB 연결도 그만큼 씀

### ClaudeApiClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/ClaudeApiClient.java
- Claude API 클라이언트 (LLM 호출 담당)
- POST https://api.anthropic.com/v1/messages
- 질문(messages) + 쓸 수 있는 도구 목록(tools)을 보내면
- Claude가 "답변" 또는 "이 도구를 써줘(tool_use)"를 돌려줌
- 도구가 필요 없는 호출(뉴스 요약 등)은 tools 에 null 을 넘기면 됨

### FrankfurterClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/FrankfurterClient.java
- 💱 환율 (Frankfurter, https://frankfurter.dev)
- 키 없이 쓰는 공개 API (유럽중앙은행 등 기관 고시 환율, 하루 한 번 바뀜)
- GET /v2/rates?base=USD&quotes=KRW,JPY&from=날짜 → [{date, base, quote, rate}, ...]

### NewsRssClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/NewsRssClient.java
- Google News RSS 클라이언트 (뉴스 검색)
- GET https://news.google.com/rss/search?q=검색어&hl=ko&gl=KR&ceid=KR:ko
- 응답: RSS(XML) → <item> 마다 title, link, pubDate, source(언론사)
- API 키가 필요 없음

### RestClients · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/RestClients.java
- 외부 API용 RestClient (시간 제한 + 🔌 연동 기록)
- RestClient.create()는 응답을 기다리는 시간 제한이 없어서, 상대 서버가 멈추면 스레드가 끝없이 기다림
- → 스케줄러 스레드(4개)가 이렇게 묶이면 다른 정해진 작업(AI 리그 09:10 등)이 아예 안 돌 수 있음
- 연결 5초, 응답은 API마다 (토스 20초, 뉴스 15초, Voyage 30초, Claude 120초, 소셜 로그인 10초)
- 연동 기록 (인터셉터)

### TossApiClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/TossApiClient.java
- 토스증권 Open API 클라이언트
- 토큰 발급과 API 호출을 한 곳에서 담당
- ★ 토스는 토큰을 새로 받으면 예전 토큰이 무효가 됨
- → 여기서 토큰을 한 번 받아 만료 전까지 재사용 (스케줄러, AI 챗봇이 같이 사용)
- 사용하는 API

### VoyageClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/VoyageClient.java
- Voyage AI 임베딩 클라이언트 (문장 → 숫자 1024개 벡터)
- POST https://api.voyageai.com/v1/embeddings
- Claude API에는 임베딩이 없어서, Anthropic이 권하는 Voyage를 씀
- input_type: 문서는 "document", 질문은 "query" (검색 정확도가 올라감)
- 무료 사용(결제 수단 없음)은 분당 요청·토큰 제한이 작아서

### WeatherClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/client/WeatherClient.java
- 🌤️ 날씨 (Open-Meteo, https://open-meteo.com) — 키 없이 쓰는 공개 API. 메인 화면 날씨 칸과 같은 곳
- 예보:   GET api.open-meteo.com/v1/forecast?latitude&longitude&current=temperature_2m
- &daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia/Seoul&forecast_days=1
- 대기질: GET air-quality-api.open-meteo.com/v1/air-quality?latitude&longitude&current=pm10
- 대기질은 못 받아도 날씨만으로 답함. 응답에 없는 값은 null (문장에서 그 줄만 빠짐)
