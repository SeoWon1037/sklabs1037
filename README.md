# SKLabs

AI 주식 비서 · 모의투자 · 중고거래를 한 곳에서 쓰는 웹 서비스예요.
혼자 만들고 혼자 운영하기 때문에, 기능만큼이나 **운영이 스스로 돌아가게 하는 장치**에 공을 들였어요.

- 서비스: https://sklabs1037.com
- 로그인 없이 바로 해보기: 첫 화면의 **체험하기** (체험 계정은 매일 새벽에 초기화돼요)

## 설계 원칙

> **AI는 정리, 판단은 서버 규칙.**

1. AI의 답은 정해진 틀(도구 호출)로만 받고, 서버가 다시 검사하고 다듬어요.
2. 되돌릴 수 없는 일(배포, 답변 발송, 삭제)은 사람이 승인해요.
3. 틀린 답을 낼 바에는 답을 내지 않아요. 계산이 맞는지 확신할 수 없으면 이유와 함께 멈춰요.

## 무엇을 할 수 있나

| 묶음 | 기능 |
| --- | --- |
| 시세 | 전 종목 시세 · 차트 · 토론, 증시 일정(휴장 · FOMC · 금통위 · 만기), AI 비서(답의 근거 표시) |
| 모의투자 | 가상 3,000만 원, 지정가 주문, KODEX 200 비교, 주간 시즌 · 명예의 전당, AI 리그, 친구 대결 |
| 놀이터 | ⏳ 그때 샀더라면(공유용 계산기), 예측 게임, 뉴스 퀴즈, 투자 성향 테스트 |
| 중고거래 | 상품 올리기(AI 글쓰기 도우미) · 채팅 · 가격 제안 · 거래 후기와 매너 온도 · 키워드 알림 · 가격 내림 알림 |
| 내 공간 | 알림 · 웹 푸시, 배지, 친구 초대, 메시지 |

화면은 휴대폰을 먼저 보고 만들었어요. 글자 크기 조절, 다크 모드, 앱 설치(PWA)를 지원해요.

## 운영이 스스로 돌아가게 한 장치

| 장치 | 하는 일 | 코드 |
| --- | --- | --- |
| QA 봇 | 배포할 때마다 실제 브라우저로 주요 화면을 열고, 규칙 위반과 화면 흐름을 점검해요. | `qa/run.mjs`, `backend/.../qa` |
| 배포 뒤 자동 되돌리기 | 서버가 안 뜨거나, 직전 점검에 없던 심각한 문제가 생기면 직전 이미지로 되돌려요. | `.github/deploy/rollback.sh`, `qa/service/DeployGuardService.java` |
| AI 자동 수정 | 문의 · QA 지적 · 화면 오류 중 코드로 고칠 것을 골라 PR을 만들어요. 손대면 안 되는 파일 검사와 빌드 · 테스트를 통과해야 하고, 합치는 건 사람이 승인해요. | `.github/workflows/ai-fix.yml`, `backend/.../autofix` |
| 화면 오류 수집 | 사용자 브라우저에서 난 오류를 모아 같은 오류는 한 줄로 묶어요. 화면이 깨져도 흰 화면 대신 안내를 보여줘요. | `frontend/src/errorReport.js`, `ops/service/ClientErrorRules.java` |
| AI 비용 한도 | 기능별 사용량을 기록하고, 하루 한도를 넘으면 AI 호출을 멈춰요. | `ops/service/AiUsageService.java` |
| DB 백업 | 매일 백업하고, 백업이 오래됐거나 실패하면 알려요. | `ops/backup/loop.sh`, `ops/service/BackupService.java` |
| 자가 점검 | 빠뜨린 SQL 파일, 서버 키, 외부 시세 받기, 계산을 버튼 하나로 확인해요. | `ops/service/SelfCheckService.java`, `SchemaExpect.java` |
| 자동 SQL | 새 표가 필요한 SQL을 서버가 켜질 때 스스로 한 번만 실행해요. | `ops/service/AutoSqlRunner.java`, `AutoSql.java` |
| 호출 제한 | 주소별로 IP당 요청 수를 제한해요. | `ops/config/RateLimitGuard.java` |

## 틀린 답을 내지 않기 위한 장치

- **그때 샀더라면**: 국내 주식은 하루 ±30%까지만 움직여요. 그보다 큰 종가 변화가 끼면 액면분할 같은 가격 기준 변경으로 보고, 답 대신 다시 계산할 날짜를 알려줘요. (`whatif/service/WhatIfCalc.java`)
- **AI 비서**: 답에 쓴 근거(시세 · 뉴스 · 일정)를 답 아래에 같이 보여줘요.
- **중고거래 글**: AI가 쓴 초안도 사람이 쓴 글과 같은 서버 규칙(금지 품목 · 길이 · 가격)으로 검사해요. (`market/service/MarketRules.java`)
- **공유 링크 미리보기**: 사용자가 쓴 제목이 HTML에 들어가므로 전부 이스케이프하고, 우리 사이트 안의 주소로만 넘겨요. (`og/OgPage.java`)

## 기술

| 영역 | 사용한 것 |
| --- | --- |
| 백엔드 | Java 17, Spring Boot 4, MyBatis(XML), PostgreSQL 18 |
| 프런트엔드 | React 19, Vite, react-router 7 (화면별 코드 나눠 받기) |
| 인프라 | AWS EC2 · RDS, Docker Compose(backend · frontend/nginx · backup), GitHub Actions |
| 외부 연동 | 토스증권 Open API(시세), Claude API, 네이버 · 카카오 로그인, 웹 푸시 |

## 폴더

```
backend/    Spring Boot 서버
  sql/        DB 표를 만드는 SQL (pgAdmin에서 직접 실행)
  src/main/java/com/sklabs1037/sklabs1037/
    stock · mock · ai · market · whatif …   기능별 패키지 (controller · service · dao)
    ops · qa · autofix                      운영 장치
frontend/   React 화면 (nginx로 서빙)
qa/         QA 봇 (Playwright)
ops/        백업 스크립트
.github/    CI · 배포 · QA 봇 · AI 자동 수정 워크플로
```

## 로컬에서 실행

필요한 것: Java 17, Node 20 이상, PostgreSQL.

1. `.env.example`을 복사해 `.env`를 만들고 값을 채워요. **`.env`와 키 값은 저장소 · 채팅 · 캡처 어디에도 올리지 않아요.**
2. `backend/sql/`의 SQL 파일을 DB에서 실행해요. (`db/auto/`의 SQL은 서버가 스스로 실행해요)
3. 백엔드와 프런트엔드를 각각 실행해요.

```
cd backend
./gradlew bootRun
```

```
cd frontend
npm install
npm run dev
```

## 테스트

```
cd backend
./gradlew test
```

```
cd frontend
npx eslint .
```

규칙과 계산은 DB나 외부 호출 없이 돌아가는 순수 함수로 떼어 테스트해요.
`SchemaExpectTest`는 `backend/sql/`에 새 표가 생겼는데 자가 점검 목록에 빠져 있으면 실패해요.

## 배포

`main`에 올리면 GitHub Actions가 이 순서로 움직여요.

1. 백엔드 · 프런트엔드 CI
2. EC2에서 이미지 빌드, 직전 이미지를 `:prev`로 남기고 교체
3. `/api/health`가 3분 안에 응답하지 않으면 되돌리기
4. QA 봇 점검 → 직전 점검에 없던 심각한 문제가 있으면 되돌리기

DB 표는 두 가지 방식으로 만들어요.

- `backend/src/main/resources/db/auto/`의 SQL은 서버가 켜질 때 아직 안 한 것만 한 번씩 스스로 실행해요. 여러 번 실행해도 되는 문장만 받고, 지우는 문장은 거부해요. (`ops/service/AutoSqlRunner.java`)
- `backend/sql/`의 예전 SQL은 직접 실행한 것들이에요.

배포 뒤 **관리자 > 자가 점검**에서 빠진 표가 없는지, 자동 SQL이 실패하지 않았는지 확인해요.

## 만든 사람

서원 · [SeoWon1037](https://github.com/SeoWon1037)

시세 · 뉴스 · AI 답변은 참고용이며, 투자 판단의 책임은 본인에게 있어요.
