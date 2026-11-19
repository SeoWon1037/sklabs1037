key: code-og
title: Spring 코드 · og 패키지
topics: spring, og
---
## 요약
🔗 공유용 주소 — 카카오톡 등에 붙이면 내용이 담긴 미리보기 카드가 뜨고, 누르면 진짜 화면으로 넘어감

클래스 2개 · API 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/og`

## API
- GET /api/og/whatif → whatIf()
- GET /api/og/market/{id} → market()

## 클래스

### OgController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/og/OgController.java
- 🔗 공유용 주소 — 카카오톡 등에 붙이면 내용이 담긴 미리보기 카드가 뜨고, 누르면 진짜 화면으로 넘어감
- 계산이 안 되거나 없는 상품이어도 오류 대신 기본 소개 카드로 (링크는 그대로 열림)
- HTML 만들기 · 이스케이프는 OgPage

### OgPage · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/og/OgPage.java
- 🔗 공유 링크 미리보기용 작은 HTML 만들기 (순수 계산)
- 카카오톡 · 슬랙 · 문자 앱은 링크의 HTML에서 og:title · og:description · og:image 만 읽어 카드로 보여줌
- (React 화면은 자바스크립트를 돌려야 내용이 생겨서, 이 앱들은 내용을 못 읽음 → 전부 같은 사이트 소개만 뜸)
- 그래서 공유용 주소(/api/og/…)는 이 HTML을 돌려주고, 사람이 열면 바로 진짜 화면으로 넘어감
- 글자는 전부 HTML 이스케이프 (상품 제목처럼 사용자가 쓴 글이 들어감)
