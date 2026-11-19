key: code-qa
title: Spring 코드 · qa 패키지
topics: spring, qa
---
## 요약
🤖 AI QA 봇

클래스 5개 · API 5개 · DB 표 1개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/qa`

## API
- POST /api/qa/report → report()
- GET /api/qa/verdict/{runId} → verdict()
- POST /api/qa/rolled-back → rolledBack()
- GET /api/admin/qa → runs()
- GET /api/admin/qa/{runId} → run()

## DB 표
- T_QA_RUN

## 클래스

### QaController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/qa/controller/QaController.java
- 🤖 AI QA 봇

### QaRunDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/qa/dao/QaRunDao.java
- 🤖 AI QA 봇 DAO (SQL: resources/mapper/QaRunMapper.xml)
- 메서드: insertRun · updateRun · selectRuns · selectRun · selectPrevRun · deleteOld

### DeployGuardService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/qa/service/DeployGuardService.java
- ⏪ 배포 지킴이 (배포 직후 문제가 생기면 바로 전 버전으로 자동 되돌리기)
- GitHub Actions(deploy.yml) 흐름
- 1. 배포 전: 지금 돌고 있는 이미지에 :prev 표시
- 2. 배포 후 1차 (서버 안에서): /api/health 가 3분 안에 OK가 아니면 → 바로 :prev 로 되돌리기
- 3. 배포 후 2차 (QA 봇): 점검 결과가 나오면 verdict(runId)로 되돌릴지 물어봄
- 메서드: verdict(runId) · rolledBack(kind, commit, reason)

### QaBotService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/qa/service/QaBotService.java
- 🤖 AI QA 봇 (서버 쪽)
- GitHub Actions(qa-bot.yml, 매일 08:30 + 배포 직후)가 진짜 브라우저로 운영 사이트를 휴대폰·PC 크기로 열고 눌러본 결과를
- 1. 토큰 확인 (QA_TOKEN, 서버 .env 와 GitHub Secrets 에 같은 값)
- 2. 저장 후 바로 응답 (분석은 별도 스레드, 작업 이름 QA_BOT → 운영 화면에 기록, 밖에서 들어온 요청이라 MANUAL로 기록 → 로컬 PC에서도 분석)
- 3. 규칙 판정 : 페이지 오류, 서버 오류(5xx), 기능 흐름 실패 → 심각 / 콘솔 에러, 가로 넘침, 6초 넘게 느림 → 주의
- 메서드: configured() · tokenOk(token) · accept(Map<String, payload) · runs() · run(runId)

### QaRunVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/qa/vo/QaRunVO.java
- 🤖 AI QA 봇 점검 기록 (T_QA_RUN)
