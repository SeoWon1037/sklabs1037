key: code-ops
title: Spring 코드 · ops 패키지
topics: spring, ops
---
## 요약
💸 AI 비용 (관리자만)

클래스 37개 · API 16개 · DB 표 21개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/ops`

## API
- GET /api/admin/ai-cost → get()
- POST /api/admin/ai-cost → update()
- GET /api/admin/api-calls → apiCalls()
- POST /api/track/error → report()
- GET /api/admin/client-errors → list()
- POST /api/admin/client-errors/{id}/status → status()
- GET /api/health → health()
- GET /api/admin/ops → status()
- POST /api/admin/ops/run → run()
- POST /api/admin/ops/test-mail → testMail()
- GET /api/admin/selfcheck → run()
- GET /api/admin/server → server()
- POST /api/admin/backup/run → backupNow()
- GET /api/status → status()
- POST /api/track → track()
- GET /api/admin/stats → stats()

## DB 표
- T_AI_BUDGET, T_AI_CHAT_LOG, T_AI_USAGE, T_API_CALL_LOG, T_BEAUTY_LOG, T_CLIENT_ERROR, T_DAILY_STAT, T_DAILY_VISITOR, T_DM_MSG, T_INQUIRY, T_JOB_RUN, T_MOCK_ORDER, T_MOCK_TRADE, T_NEWS, T_NEWS_QUIZ_PLAY, T_NEWS_SUMMARY, T_PREDICT_VOTE, T_PRICE_ALERT, T_STOCK, T_STOCK_POST, T_USER

## 클래스

### RateLimitGuard · Config
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/config/RateLimitGuard.java
- 🚦 요청 횟수 제한 (IP별, 서버 메모리)
- 로그인 비밀번호 대입 · 인증 메일 폭탄 · 체험 계정 남용 · 문의·상품 도배 · 통계 부풀리기 · AI 비용 폭주를 앞에서 막음
- IP는 nginx가 넣어 주는 X-Real-IP (백엔드는 nginx 뒤에만 있어서 사용자가 바꿀 수 없음)
- 넘으면 429 + Retry-After (몇 초 뒤에 다시)
- 서버가 1대라 메모리로 충분 (여러 대가 되면 Redis로 옮길 자리)

### AiCostController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/AiCostController.java
- 💸 AI 비용 (관리자만)

### ApiCallLogController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/ApiCallLogController.java
- 🔌 외부 연동 현황 (관리자만)

### ClientErrorController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/ClientErrorController.java
- 🐞 화면 오류

### HealthController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/HealthController.java
- ⏪ 배포 직후 살아 있는지 확인 (deploy.yml이 3분 동안 부름 → OK가 아니면 바로 전 버전으로 되돌림)
- 서버가 떴고 DB에 연결되는지만 봄 (외부 서비스 장애로 배포를 되돌리지 않게)
- 로그인 없이 누구나, 내부 정보는 내보내지 않음

### OpsAdminController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/OpsAdminController.java
- 관리자: 운영 상태 Controller
- 로그인 + T_USER.ROLE = 'ADMIN' 인 회원만 (매 요청마다 DB에서 확인, AI 피드백 화면과 같은 방식)

### SelfCheckController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/SelfCheckController.java
- 🧪 자가 점검 (관리자만)
- 토스를 실제로 부르는 점검이 있어 몇 초 걸릴 수 있음

### ServerMonitorController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/ServerMonitorController.java
- 🖥 서버 상태 (관리자만)
- 화면이 5초마다 다시 부름 (값은 서버가 이미 재 둔 것 → 화면 여러 개를 열어도 DB 부담 같음)

### StatusController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/StatusController.java
- 🟢 서비스 상태 (로그인 없이 누구나, /status 화면)

### UsageStatController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/controller/UsageStatController.java
- 📊 사용 통계

### AiUsageDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/AiUsageDao.java
- 💸 AI 비용 DAO (SQL: resources/mapper/AiUsageMapper.xml)
- 메서드: upsert · selectTodayCost · selectBudget · updateBudget · selectDays · selectTodayFeatures · selectMonth

### ApiCallLogDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/ApiCallLogDao.java
- 🔌 외부 연동 기록 Dao (SQL: resources/mapper/ApiCallLogMapper.xml)
- 메서드: insertBatch · selectServiceStats · selectHourly · selectActionStats · selectRecentFails · deleteOld

### ClientErrorDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/ClientErrorDao.java
- 🐞 화면 오류 DAO (SQL: resources/mapper/ClientErrorMapper.xml)
- 메서드: bump · insert · countNewToday · selectList · selectCounts · updateStatus · selectCandidates · deleteOld

### JobRunDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/JobRunDao.java
- 작업 실행 기록 Dao
- 메서드 이름 = JobRunMapper.xml의 id
- 메서드: insertRun · finishRun · insertFinishedRun · abortStaleRuns · selectRecentRuns · selectLatestPerJob · countSuccessSince · countProblemsSince · deleteOldRuns · selectDataHealth · selectAdminEmails

### SelfCheckDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/SelfCheckDao.java
- 🧪 자가 점검 DAO (SQL: resources/mapper/SelfCheckMapper.xml) — DB에 실제로 있는 표 · 칸 이름 읽기
- 메서드: selectTables · selectColumns

### ServerMonitorDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/ServerMonitorDao.java
- 🖥 DB 서버 상태 (PostgreSQL 통계 뷰, SQL: resources/mapper/ServerMonitorMapper.xml)
- pg_stat_database / pg_stat_activity / pg_stat_user_tables 는 PostgreSQL 기본 통계 → 따로 설치할 것 없음
- RDS 의 CPU·디스크 IO 같은 장비 숫자는 CloudWatch 에만 있어서 여기서는 DB가 직접 알려주는 것만
- 메서드: ping · selectDbStats · selectConnStates · selectLongestActive · selectTopTables · selectAdminIds

### UsageStatDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/dao/UsageStatDao.java
- 📊 사용 통계 DAO (SQL: resources/mapper/UsageStatMapper.xml)
- 메서드: addStat · addVisitors · deleteOld · deleteOldVisitors · selectDaily · selectPages · selectEvents · selectSignups · selectFeatures

### BackupCheckScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/scheduler/BackupCheckScheduler.java
- 💾 DB 백업 확인 스케줄러 (매일 06:00, 04:30 백업이 끝났을 시간)
- 작업 내용: BackupService.check / 기록·실패 메일: OpsJobs → JobRunService

### OpsWatchdogScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/scheduler/OpsWatchdogScheduler.java
- 작업 감시 (정해진 시각에 작업이 "성공"했는지 확인)
- 실패는 JobRunService가 바로 메일을 보내지만, 서버가 꺼져 있었거나 스케줄이 아예 안 돈 경우는 실패 기록조차 없음
- → 이 감시가 "돌았어야 할 작업이 안 돌았다"를 잡아냄 (2026-10-19 20:30 수집이 조용히 빠진 일이 계기)
- 평일 21:30: 오늘 20:30 시세 수집 성공 기록이 없으면 → 메일 + 놓친 시세 따라잡기 자동 실행
- 평일 15:50: 오늘 15:40 모의투자 자산 기록이 없으면 → 메일 + 다시 실행

### AiUsageService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/AiUsageService.java
- 💸 AI 비용 안전장치 (하네스: AI를 쓰는 모든 기능을 한 곳에서 지킴)
- ClaudeApiClient가 Claude를 부르기 전 / 후에 이 서비스를 거침
- 전: check()   → 긴급 정지 중이거나 오늘 쓴 돈 ≥ 하루 한도면 AiBudgetException (Claude를 부르지 않음)
- 후: record()  → 응답의 usage(토큰 수)로 비용 계산 → T_AI_USAGE에 날짜 · 기능별로 더함
- → 한도의 80% · 100%에 관리자 메일 + 📲 (하루 한 번씩)
- 메서드: Prices(input, output, cacheWrite, cacheRead, longThreshold, longMultiplier) · check() · record(feature, Map<String, response, failed) · dashboard() · paused() · blocked() · update(userId, usd, pause) · cost(in, out, cacheRead, cacheWrite, p) · feature(frames)

### ApiCallLogService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/ApiCallLogService.java
- 🔌 외부 연동 모니터링
- 1. 저장 (10초마다): ApiCallLog 큐에 쌓인 기록을 500줄씩 묶어서 INSERT
- 로컬 PC에서는 저장 안 함 (로컬과 운영이 같은 DB라 운영 기록에 섞이지 않게)
- 2. 경보 (5분마다): 최근 15분 서비스별 실패율을 보고 관리자에게 메일 + 📲
- 메일(SMTP)이 고장 나면 메일 경보는 못 가니까 📲 푸시도 같이 보냄
- 메서드: flush() · Alert(key, service, calls, fails, lastError) · checkAlerts() · cleanUp() · dashboard()

### AutoSql · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/AutoSql.java
- 🗄️ 자동 SQL 파일 다루기 (순수 계산)
- split: SQL 글을 문장 단위로 나눔 (주석 · 따옴표 · $$ 안의 ; 은 나누지 않음)
- lint: 서버가 스스로 실행해도 되는 파일인지 검사
- 여러 번 실행해도 같은 결과여야 함 → CREATE TABLE / INDEX, ADD COLUMN 은 IF NOT EXISTS 필수
- 지우는 문장은 안 됨 → DROP · TRUNCATE · DELETE 금지 (그런 일은 사람이 pgAdmin에서 직접)

### AutoSqlRunner · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/AutoSqlRunner.java
- 🗄️ 자동 SQL — 서버가 켜질 때 resources/db/auto/*.sql 중 아직 안 한 것을 이름순으로 한 번씩 실행
- 왜: 지금까지는 새 표가 필요할 때마다 pgAdmin에서 SQL 파일을 손으로 실행해야 했음 (빼먹으면 그 기능이 깨짐)
- → 앞으로 새로 만드는 SQL은 db/auto 에 넣고, 서버가 스스로 실행. (예전 backend/sql 파일들은 이미 실행한 것이라 건드리지 않음)
- 안전장치
- 파일마다 하나의 묶음(트랜잭션)으로 실행 → 중간에 실패하면 그 파일은 없던 일로, 뒤 파일은 실행하지 않음
- 메서드: onReady() · status()

### BackgroundJobGuard · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/BackgroundJobGuard.java
- 백그라운드 작업(스케줄러 · 서버 시작 작업)을 이 서버에서 돌릴지
- 왜 필요한가 (2026-10-20 장애)
- 로컬 PC와 운영 서버가 같은 토스 키를 씀 → 토스는 키 하나에 토큰 하나만 유효
- 로컬에서 코드를 받아 서버가 다시 켜지는 순간 운영 서버와 동시에 3,000종목 수집을 시작
- → 서로 토큰을 새로 받아 상대 토큰을 무효로 만듦(401 invalid-token) → 대부분 실패 + 장애 메일

### BackupService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/BackupService.java
- 💾 DB 백업 상태 (백업 자체는 docker-compose의 backup 컨테이너가 함: ops/backup/loop.sh)
- backup 컨테이너와 같은 폴더(/backups)를 같이 씀
- sklabs-YYYYMMDD-HHMM.dump  백업 파일 (최근 7개)
- last.json                  마지막 시도 결과 { ok, file, at(초), seconds }
- last-error.txt             마지막 실패 이유
- 메서드: status() · runNow() · check()

### ClientErrorRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/ClientErrorRules.java
- 🐞 화면 오류 보고 다듬기 (순수 계산)
- 누구나 보낼 수 있는 주소라서, 받은 글은 길이를 자르고 줄 수를 줄여 저장
- 우리 코드 문제가 아닌 것은 받지 않음: 브라우저 확장 프로그램, 다른 사이트 스크립트("Script error."),
- 인터넷이 끊겨 생긴 실패, 브라우저가 스스로 정리하는 경고
- 지문(sig): 같은 오류를 한 줄로 묶기 위한 값 — 숫자 · 빌드마다 바뀌는 파일 이름을 지운 메시지 + 스택 첫 줄
- 메서드: Report(sig, kind, message, stack, path, ua) · clean(Map<String, in, userAgent)

### ClientErrorService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/ClientErrorService.java
- 🐞 화면 오류 모으기 — 사용자 브라우저에서 난 오류를 서버가 모아 관리자에게 보여줌
- 화면(frontend/src/errorReport.js)이 POST /api/track/error 로 보냄 (로그인 없이도, 한 사람이 한 번 접속에 5개까지)
- 같은 오류는 한 줄에 횟수만 올림 (ClientErrorRules.sig). 고쳤다고 해둔 오류가 또 생기면 다시 OPEN
- 하루에 새 오류 종류는 200개까지만 받음 (누가 일부러 쏟아부어도 표가 불어나지 않게), 30일 지나면 지움
- 최근 이틀 안에 2번 이상 난 오류는 🔁 매일 개선(DailyImproveService)의 후보가 됨 → AI가 고치고 사람이 승인
- 메서드: report(Map<String, in, userAgent) · list(status) · status(id, status) · cleanup()

### JobRunService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/JobRunService.java
- 작업 실행 기록 Service
- jobRunService.execute("STOCK_DAILY", "SCHEDULE", () -> { ...; return JobOutcome.of(전체, 실패, "요약"); });
- 시작할 때 RUNNING 1줄 → 끝나면 SUCCESS / PARTIAL(일부 실패) / FAIL(예외)
- 같은 작업이 이미 돌고 있으면 건너뜀 (관리자 수동 실행과 스케줄이 겹쳐도 한 번만)
- FAIL이거나 실패가 10% 이상이면 관리자에게 메일
- 메서드: JobOutcome(total, failed, message) · abortLeftovers() · execute(job, trigger, body) · recordFailure(job, trigger, e) · backgroundEnabled() · isRunning(job) · runningJobs() · label(job)

### MailCallRecorder · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/MailCallRecorder.java
- 🔌 메일(네이버 SMTP) 발송도 연동 기록에 남기기
- 메일을 보내는 곳이 5군데(회원가입 인증, 계정 찾기, 가격 알림, 아침 브리핑, 관리자 장애 메일)라
- 하나하나 고치지 않고, 스프링이 만든 JavaMailSender를 "기록하는 대리인"으로 한 번 감쌈
- BeanPostProcessor: 스프링이 빈을 만든 직후 끼어들어 다른 객체로 바꿔 끼울 수 있는 자리
- Proxy: JavaMailSender 모양 그대로인 대리 객체 → send…() 가 불리면 시간을 재고 진짜 객체에 넘김

### OpsAlertService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/OpsAlertService.java
- 관리자 장애 알림 메일
- 받는 사람: T_USER.ROLE = 'ADMIN' 인 회원의 이메일 (설정 따로 없음)
- 같은 알림(key)은 1시간에 한 번만 → 1분마다 실패하는 작업이 메일 폭탄이 되지 않게
- 메일 발송이 실패해도 작업에는 영향 없음 (로그만)
- 메서드: alert(key, title, body) · send(subject, body)

### OpsJobs · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/OpsJobs.java
- 운영 작업 모음 (스케줄러와 관리자 "지금 실행" 버튼이 같은 작업을 쓰게)
- opsJobs.run("STOCK_DAILY", "SCHEDULE")   스케줄러
- opsJobs.runAsync("STOCK_DAILY")          관리자 화면 (별도 스레드, 바로 응답)
- 실행 기록·실패 메일은 JobRunService가 맡음

### SchemaExpect · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/SchemaExpect.java
- 🧪 "DB에 있어야 하는 표 · 칸" 목록 (순수 계산)
- backend/sql/*.sql 을 pgAdmin에서 손으로 실행하는 방식이라, 하나를 빼먹어도 그 기능을 누를 때까지 모름
- → 관리자 > 자가 점검이 이 목록과 실제 DB를 비교해 "아직 실행 안 한 SQL 파일"을 알려줌
- 목록은 backend/sql 과 resources/db/auto 의 CREATE TABLE / ALTER TABLE … ADD COLUMN 에서 뽑은 것 (표 이름 → 그 표를 만드는 파일)
- 파일 이름이 날짜로 시작하면(2026-11-11-01-…) 서버가 스스로 실행하는 자동 SQL (AutoSqlRunner)

### SelfCheckService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/SelfCheckService.java
- 🧪 자가 점검 — 배포한 뒤 "제대로 붙었는지"를 버튼 하나로 확인 (관리자 > 자가 점검)
- 왜: SQL은 pgAdmin에서 손으로 실행하고, 토스 · AI · GitHub 키는 서버 .env에 있어서
- 하나가 빠져도 그 기능을 누를 때까지 모름. 그리고 개발 환경에서는 토스를 실제로 부를 수 없어 운영에서만 확인되는 것이 있음
- 점검 항목 (하나가 실패해도 나머지는 계속)
- 1. DB 표 · 칸: sql 폴더가 만드는 것과 실제 DB 비교 → 아직 실행 안 한 SQL 파일 (SchemaExpect)
- 메서드: run()

### ServerMonitorService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/ServerMonitorService.java
- 🖥 서버 상태 (운영 서버 + DB 서버) — 관리자 화면에서 5초마다 실시간
- 5초마다 한 번 재서 최근 1시간(720개)을 메모리에만 둠 (DB에 안 쌓음 → 가볍고, 배포하면 처음부터)
- 운영 서버(EC2 · Docker 컨테이너 안 Spring Boot)
- CPU: 서버 전체 / 우리 앱(이 Java 프로세스) — JDK가 컨테이너 한도를 알아서 반영
- 메모리: 서버 전체 / Java 힙, 디스크(/), 스레드, 켜진 시간, 실시간 접속(토론방·메시지 SSE)
- 메서드: Sample(t, cpu, appCpu, memUsed, memTotal, heapUsed, heapMax, diskUsed, diskTotal, threads, live, dbOk, dbMs, dbConn, dbConnAll, dbMax, dbTps, cacheHit, longestSec, swapUsed, swapTotal) · sample() · snapshot() · Alert(key, title, body)

### StatusService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/StatusService.java
- 🟢 서비스 상태 (누구나 보는 /status 화면)
- 지금 사이트가 제대로 도는지 사용자에게 투명하게: 웹 · DB · 주식 시세 · 뉴스 · AI 기능 · 최근 자동 점검(QA 봇)
- 상태: OK 정상 / WARN 늦어짐·일부 멈춤 / DOWN 장애
- 30초 동안 같은 결과를 재사용 (많이 열어도 DB 부담 없게), 내부 정보(에러 내용·주소)는 내보내지 않음
- 메서드: status()

### UsageStatService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/service/UsageStatService.java
- 📊 사용 통계 (방문자 · 화면 열기 · 이벤트)
- 브라우저가 화면을 열 때마다 POST /api/track { p: 주소, v: 브라우저 무작위 번호 }
- → 메모리에 더해 두고 1분마다 DB에 한 번에 저장 (요청마다 DB를 두드리지 않게)
- 주소는 첫 칸만 (/stock?symbol=005930 → /stock), 관리자 화면 · 봇(검색 로봇 · QA 봇의 헤드리스 브라우저)은 세지 않음
- 방문자 번호는 서버 비밀값과 섞어 해시한 앞 16자만 저장 (원래 번호 · IP · 회원 번호는 저장 안 함)
- 메서드: pageView(path, vid, userAgent) · event(key) · flush() · dashboard()

### JobRunVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/ops/vo/JobRunVO.java
- 작업 실행 기록 VO
- 사용 테이블: T_JOB_RUN (+ 데이터 상태 확인용 T_STOCK, T_NEWS, T_NEWS_SUMMARY)
