key: code-whatif
title: Spring 코드 · whatif 패키지
topics: spring, whatif
---
## 요약
⏳ "그때 샀더라면" (로그인 없이 누구나)

클래스 4개 · API 1개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/whatif`

## API
- GET /api/whatif → calc()

## DB 표
- T_STOCK, T_STOCK_PRICE

## 클래스

### WhatIfController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/whatif/controller/WhatIfController.java
- ⏳ "그때 샀더라면" (로그인 없이 누구나)
- → { success, stock, amount, buyDate, buyPrice, nowDate, nowPrice, value, profit, rate, days,
- clamped, firstDate, peak, trough, series[{d,v}], bench }
- 가격 기준이 바뀐 날이 끼면 { success:false, message, breakDate, retryFrom }

### WhatIfDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/whatif/dao/WhatIfDao.java
- ⏳ "그때 샀더라면" DAO (SQL: resources/mapper/WhatIfMapper.xml) — 읽기만 함
- 메서드: selectStock · selectCloses

### WhatIfCalc · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/whatif/service/WhatIfCalc.java
- ⏳ "그때 샀더라면" 계산 (DB · 외부 호출 없는 순수 계산 → 테스트로 검증)
- 계산 방법
- 산 날: 고른 날짜 또는 그 뒤 첫 거래일의 종가 (고른 날짜가 가진 기록보다 이르면 가장 이른 거래일로 당기고 clamped = true)
- 지금: 가장 최근 거래일의 종가
- 지금 가치 = 넣은 돈 × 지금 종가 ÷ 산 날 종가 (소수점 주식까지 샀다고 가정, 원 단위 반올림)

### WhatIfService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/whatif/service/WhatIfService.java
- ⏳ "그때 샀더라면" — 종목 · 날짜 · 금액을 넣으면 지금 얼마가 됐을지 (로그인 없이 누구나)
- 시세는 어디서
- 우리 DB(T_STOCK_PRICE)에는 최근 200거래일쯤만 있음 → 더 옛날은 토스증권 일봉을 200개씩 이어 받아 씀 (최대 8쪽 ≈ 6년 반)
- 토스 일봉은 한 번에 최대 200개, 응답의 nextBefore를 before로 넘겨 그 앞 구간을 받음. 수정주가(액면분할 반영)가 기본
- 받은 긴 시세는 DB에 넣지 않고 메모리에만 6시간 둠 (종목 화면 차트 · 다른 계산에 영향을 주지 않게)
- 메서드: calc(rawSymbol, rawFrom, amount) · calc(rawSymbol, rawFrom, amount, count) · probe(rawSymbol, from)
