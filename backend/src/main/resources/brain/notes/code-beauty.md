key: code-beauty
title: Spring 코드 · beauty 패키지
topics: spring, beauty
---
## 요약
뷰티 랩 Controller

클래스 16개 · API 13개 · DB 표 4개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/beauty`

## API
- POST /api/beauty/analyze → analyze()
- POST /api/beauty/brief → brief()
- POST /api/beauty/formula/check → formulaCheck()
- POST /api/beauty/shelf/check → shelfCheck()
- POST /api/beauty/adcopy/check → adCopyCheck()
- POST /api/beauty/adcopy/rewrite → adCopyRewrite()
- GET /api/beauty/ingredients → ingredients()
- GET /api/beauty/history → history()
- GET /api/beauty/history/{logId} → historyOne()
- GET /api/beauty/regulation/docs → docs()
- POST /api/beauty/regulation/docs → upload()
- DELETE /api/beauty/regulation/docs/{docId} → delete()
- POST /api/beauty/regulation/ask → ask()

## DB 표
- T_BEAUTY_LOG, T_INGREDIENT, T_RAG_CHUNK, T_RAG_DOC

## 클래스

### BeautyLabController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/controller/BeautyLabController.java
- 뷰티 랩 Controller
- image: "data:image/jpeg;base64,..." (화면에서 1568px 이하로 줄여서 보냄) / text: 전성분 붙여넣기

### RegulationController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/controller/RegulationController.java
- 화장품 규제 Q&A Controller (RAG)

### BeautyLabDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/dao/BeautyLabDao.java
- 뷰티 랩 Dao
- 메서드 이름 = BeautyLabMapper.xml의 id
- 메서드: selectIngredients · insertLog · countToday · selectMyLogs · selectMyLog

### RegulationDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/dao/RegulationDao.java
- 규제 Q&A Dao
- 메서드 이름 = RegulationMapper.xml의 id
- 메서드: insertDoc · updateDoc · selectDocs · deleteDoc · insertChunk · searchChunks · failStaleDocs

### AdCopyRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/AdCopyRules.java
- 📣 화장품 광고 문구 검수 규칙 (AI 없이 규칙만)
- 근거 (요약)
- 「화장품법」 제13조 부당한 표시·광고 금지
- ① 1. 의약품으로 잘못 인식할 우려  2. 기능성화장품이 아닌데 기능성으로 오인
- 3. 천연·유기농 화장품이 아닌데 오인  4. 소비자를 속이거나 잘못 알게 할 우려
- 메서드: Rule(code, level, category, pattern, reason, suggest, basis, allowWith) · Finding(start, end, phrase, level, category, code, reason, suggest, basis) · check(text, claims) · verdict(findings)

### AdCopyService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/AdCopyService.java
- 📣 광고 문구 검수 (뷰티 랩)
- 1. 검사 (누구나, AI 없음): AdCopyRules로 걸리는 표현 · 이유 · 근거 · 바꿔 쓸 방향
- 2. ✨ AI로 고쳐 쓰기 (로그인, 하루 20번): 걸린 표현 목록을 주고 광고 느낌은 살려 다시 쓰게 함
- → 서버가 고친 문구를 같은 규칙으로 다시 검사 (AI가 "고쳤다"고 해도 규칙이 최종 판단)
- → 기능성은 사용자가 고른 것만, 공식 효능 문구로
- 메서드: check(text, claims) · rewrite(userId, text, claims) · remaining(userId)

### BeautyLabService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/BeautyLabService.java
- 뷰티 랩 Service
- 1. 성분 분석 (analyze)
- 사진: Claude Vision이 전성분을 "표기 순서 그대로 읽기만" (정해진 도구 형식으로 받음 → JSON 모양이 항상 같음)
- 글: 붙여 넣은 전성분을 서버가 쉼표·줄바꿈으로 나눔 (AI 호출 없음, 비용 0)
- 판단(역할·기능성·알레르기·피부 타입 주의)은 직접 정리한 성분 사전(T_INGREDIENT)으로
- 메서드: analyze(userId, imageBase64, mediaType, text, skinType) · compare(userId, Map<String, a, Map<String, b, skinType) · brief(userId, request) · myLogs(userId) · myLog(userId, logId) · remaining(userId, kind)

### FormulaCheckService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/FormulaCheckService.java
- 🧪 처방 검토기 (AI 없이 규칙만으로)
- 입력: 원료 이름 + 함량(%) 목록, 하나는 "적량"(나머지 채우기, 보통 정제수) 가능
- 제품 유형(씻어내지 않는 / 씻어내는), 내세울 기능성(미백·주름·여드름), 어린이용 여부
- 결과
- 1. 합계: 적량을 채운 뒤 100%인지 (넘거나 모자라면 오류)
- 메서드: Item(name, pct, qs) · check(input, rinseOff, claims, kids)

### FormulaRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/FormulaRules.java
- 처방 검토 규칙 (숫자는 전부 고시 원문 기준, AI가 판단하지 않음)
- 출처
- A. 「화장품 안전기준 등에 관한 규정」 [별표 2] 사용상의 제한이 필요한 원료 (보존제·자외선 차단 성분 사용한도)
- B. 「기능성화장품 심사에 관한 규정」 [별표 4] 자료제출이 생략되는 기능성화장품의 종류 (미백·주름·여드름 고시 함량)
- C. 「화장품 사용 시의 주의사항 및 알레르기 유발성분 표시에 관한 규정」 착향제 알레르기 유발성분
- 메서드: Limit(names, leaveOn, rinseOff, factor, group, kidsBanned, category, basis) · GroupLimit(limit, label, basis) · Functional(names, claim, min, max, basis, note, rinseOffOnly) · limitOf(name) · functionalsOf(name)

### IngredientDictionary · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/IngredientDictionary.java
- 화장품 성분 사전 (T_INGREDIENT를 메모리에 올려두고 비교)
- 150개 남짓이라 매번 DB를 뒤지지 않고 10분마다 다시 읽음
- 표기 차이를 맞춰서 비교 (normalize)
- "메칠파라벤" = "메틸파라벤", "살리실릭애씨드" = "살리실릭애시드", "부틸렌글라이콜" = "부틸렌글리콜",
- 띄어쓰기·하이픈·괄호 무시, 영문 INCI 대소문자 무시

### RegulationQaService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/RegulationQaService.java
- 화장품 규제 Q&A (RAG: 검색 증강 생성)
- 문서 올리기 (관리자)
- PDF·TXT → 쪽별 글자 추출(PDFBox) → 문단 단위로 쪼개기(약 700자, 앞 문단 끝 120자 겹침, "제○조" 앞에서 끊기)
- → Voyage로 벡터(1024) → T_RAG_CHUNK (pgvector) → READY
- 별도 스레드에서 처리 (큰 문서는 몇 분), 화면에서 진행 상황(문단 수) 확인
- 메서드: failLeftovers() · docs() · delete(docId) · ready() · upload(userId, title, source, fileName, bytes) · ask(userId, question)

### ShelfService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/service/ShelfService.java
- 🧴 내 화장대 · 성분 궁합 (AI 없이 사전 + 규칙)
- 입력: 제품 여러 개 (이름, 전성분 글, 단계: 클렌저·토너·세럼·크림·선크림, 시간: 아침·저녁·둘 다), 피부 타입(선택)
- 결과
- 1. 제품마다: 사전에서 찾은 성분 수, 주요 활성 성분(레티노이드·AHA·BHA·순수 비타민C·나이아신아마이드·자외선 차단 등),
- 알레르기 착향제, 내 피부 타입에 주의 성분
- 메서드: Product(name, ingredients, step, time) · check(input, skinType)

### BeautyLogVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/vo/BeautyLogVO.java
- 뷰티 랩 기록 VO (성분 분석 / 기획 브리프)
- 사용 테이블: T_BEAUTY_LOG

### IngredientVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/vo/IngredientVO.java
- 화장품 성분 사전 VO
- 사용 테이블: T_INGREDIENT

### RagChunkVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/vo/RagChunkVO.java
- 규제 Q&A 문단(청크) VO
- 사용 테이블: T_RAG_CHUNK (+ T_RAG_DOC)

### RagDocVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/beauty/vo/RagDocVO.java
- 규제 Q&A 문서 VO
- 사용 테이블: T_RAG_DOC
