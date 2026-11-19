key: code-autofix
title: Spring 코드 · autofix 패키지
topics: spring, autofix
---
## 요약
🤖 AI 자동 수정

클래스 6개 · API 7개 · DB 표 5개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/autofix`

## API
- POST /api/admin/inquiries/{id}/autofix → request()
- GET /api/admin/autofix → list()
- GET /api/admin/autofix/count → count()
- POST /api/admin/autofix/{id}/approve → approve()
- POST /api/admin/autofix/{id}/reject → reject()
- POST /api/admin/autofix/daily → daily()
- POST /api/autofix/callback → callback()

## DB 표
- T_API_CALL_LOG, T_AUTOFIX, T_INQUIRY, T_JOB_RUN, T_QA_RUN

## 클래스

### AutofixController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/autofix/controller/AutofixController.java
- 🤖 AI 자동 수정
- 관리자

### AutofixDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/autofix/dao/AutofixDao.java
- 🤖 AI 자동 수정 DAO (SQL: resources/mapper/AutofixMapper.xml)
- 메서드: insertRun · countToday · countActive · selectRun · selectByIssue · selectRuns · countReady · updateFromCallback · handle · selectLatestQaIssues · selectApiFailures · selectJobFailures · selectRecentSignatures · countAnyActive

### DailyImproveScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/autofix/scheduler/DailyImproveScheduler.java
- 🔁 매일 스스로 개선 스케줄러 (매일 09:20 — 08:30 QA 봇 결과까지 본 뒤)
- 작업 내용: DailyImproveService.run / 기록·실패 메일·로컬 PC에서 끄기: OpsJobs → JobRunService

### AutofixService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/autofix/service/AutofixService.java
- 🤖 AI 자동 수정 (문의 → 코드 수정 → 테스트 → 승인 → 배포)
- 흐름
- 1. 관리자가 문의함에서 [🤖 AI로 고치기] (AI 분류가 "자동 수정 후보" + 위험 낱말 없음인 문의만)
- 2. 서버가 GitHub에 "ai-fix" 이슈 (문의 요약 + 고칠 곳 힌트 + 원문은 "자료"로 감싸서)
- 3. .github/workflows/ai-fix.yml: Claude Code(명령 실행 없이 읽기·수정 도구만)가 고침
- 메서드: request(adminId, inquiryId) · requestSystem(title, body, signature) · callback(Map<String, req) · approve(adminId, id) · reject(adminId, id, note) · list() · githubConfigured() · dailyLimit() · readyCount()

### DailyImproveService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/autofix/service/DailyImproveService.java
- 🔁 매일 스스로 개선 (작업 DAILY_IMPROVE, 매일 09:20 — 아침 QA 봇(08:30) 결과까지 보고)
- 문의가 없어도 서버가 자기 운영 기록에서 고칠 거리를 찾아 AI 자동 수정으로 보냄
- 1. 후보 모으기 (최근 이틀): 🤖 QA 봇 지적 · 🐞 사용자 화면 오류(2번 이상) · 🔌 외부 연동 실패 · 🛠 작업 실패
- 최근 14일 안에 이미 고른 문제(SIGNATURE)는 빼고
- 2. Claude(pick_improvement 강제 도구)가 "이 저장소 코드를 조금 고쳐서 해결될" 것 하나만 고름
- 메서드: Candidate(signature, kind, title, evidence) · run()

### GitHubClient · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/autofix/service/GitHubClient.java
- GitHub REST API (이 저장소 하나만)
- 토큰: 서버 .env 의 GITHUB_TOKEN (이 저장소만 · Issues/Pull requests/Contents 읽기·쓰기만 준 fine-grained 토큰)
- → 이 토큰으로 PR을 합치면 main push로 기존 배포(deploy.yml)가 그대로 돌아감
- (Actions 안의 기본 GITHUB_TOKEN으로 합치면 다음 워크플로가 안 돌기 때문에 서버가 합침)
- 저장소: GITHUB_REPO (기본 SeoWon1037/sklabs1037)
