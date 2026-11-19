key: code-user
title: Spring 코드 · user 패키지
topics: spring, user
---
## 요약
아이디 찾기 / 비밀번호 재설정 Controller

클래스 29개 · API 20개 · DB 표 5개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/user`

## API
- GET /api/user/find/id → findId()
- POST /api/user/find/reset/send-code → sendCode()
- POST /api/user/find/reset/verify → verify()
- POST /api/user/find/reset/password → resetPassword()
- POST /api/user/login → login()
- GET /api/user/me → me()
- POST /api/user/logout → logout()
- GET /api/user/mypage → myInfo()
- POST /api/user/mypage/nickname → changeNickname()
- POST /api/user/mypage/password → changePassword()
- POST /api/user/mypage/withdraw → withdraw()
- GET /api/user/mypage/chats → myChats()
- GET /api/oauth/naver/login → naverLogin()
- GET /api/oauth/naver/callback → naverCallback()
- GET /api/oauth/kakao/login → kakaoLogin()
- GET /api/oauth/kakao/callback → kakaoCallback()
- GET /api/user/signup/check-email → checkEmail()
- POST /api/user/signup/send-code → sendCode()
- POST /api/user/signup/verify-code → verifyCode()
- POST /api/user/signup → signUp()

## DB 표
- T_AI_CHAT_LOG, T_EMAIL_VERIFY, T_LOGIN_KEEP, T_USER, T_WATCHLIST

## 클래스

### UserFindController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/controller/UserFindController.java
- 아이디 찾기 / 비밀번호 재설정 Controller

### UserLoginController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/controller/UserLoginController.java
- 로그인 화면 Controller
- 로그인 성공 시 회원 정보를 "세션"에 저장 (서버 메모리)
- 브라우저는 번호표 쿠키(JSESSIONID)만 갖고 다니고, 요청마다 자동으로 보냄

### UserMyPageController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/controller/UserMyPageController.java
- 마이페이지 Controller

### UserOAuthController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/controller/UserOAuthController.java
- 소셜 로그인 Controller (네이버, 카카오)
- JSON이 아니라 "다른 주소로 이동(redirect)"을 돌려주는 Controller
- 로그인 결과는 항상 프론트의 /oauth/result?status=... 로 보냄
- (팝업이면 그 페이지가 원래 창에 결과를 알려주고 스스로 닫힘)
- status 값

### UserSignUpController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/controller/UserSignUpController.java
- 회원가입 화면 Controller
- 프론트(React)의 요청을 받아서 Service로 넘기고, 결과를 JSON으로 돌려줌
- @RestController: 화면(HTML)이 아니라 데이터(JSON)를 돌려주는 Controller

### UserFindDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/dao/UserFindDao.java
- 아이디 찾기 / 비밀번호 재설정 Dao
- 메서드 이름 = UserFindMapper.xml의 id
- 메서드: selectUsersByNickname · selectUserByEmail · countRecentSend · insertResetCode · selectLatestResetCode · updateVerifySuccess · updatePassword

### UserLoginDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/dao/UserLoginDao.java
- 로그인 화면 Dao
- @Mapper: MyBatis가 이 interface를 보고 실제 동작하는 코드를 자동으로 만들어줌
- 메서드 이름 = UserLoginMapper.xml의 id
- 메서드: selectUserByEmail

### UserMyPageDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/dao/UserMyPageDao.java
- 마이페이지 Dao
- 메서드 이름 = UserMyPageMapper.xml의 id
- 메서드: selectMyInfo · updateNickname · updatePassword · withdrawUser · deleteWatchlist · selectMyChats · countMyChats

### UserOAuthDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/dao/UserOAuthDao.java
- 소셜 로그인 화면 Dao
- 메서드 이름 = UserOAuthMapper.xml의 id
- 메서드: selectUserByProvider · countByEmail · insertOAuthUser

### UserSignUpDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/dao/UserSignUpDao.java
- 회원가입 화면 Dao
- @Mapper: MyBatis가 이 interface를 보고 실제 동작하는 코드를 자동으로 만들어줌
- 메서드 이름 = UserSignUpMapper.xml의 id
- 메서드: countByEmail · insertUser · countRecentSend · insertEmailVerify · selectLatestVerify · updateVerifySuccess · countVerified · deleteEmailVerify

### KeepLoginDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/keep/KeepLoginDao.java
- 🔑 로그인 유지 DAO (SQL: resources/mapper/KeepLoginMapper.xml)
- 메서드: insert · selectUser · touch · delete · deleteByUser · deleteByEmail · deleteExpired

### KeepLoginGuard · Config
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/keep/KeepLoginGuard.java
- 🔑 로그인 유지: 모든 /api 요청이 컨트롤러에 닿기 전에, 세션이 비어 있고 표(쿠키)가 있으면 로그인 상태를 되살림
- 컨트롤러들은 지금처럼 세션의 LOGIN_USER만 보면 됨 (코드 수정 없음)
- 로그아웃 요청은 되살리지 않음 (어차피 지울 것이므로)

### KeepLoginRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/keep/KeepLoginRules.java
- 🔑 로그인 유지 규칙 (순수 계산 → 테스트 가능)
- 표(token): 무작위 32바이트 → URL에 써도 되는 글자 43자. 브라우저 쿠키에만 있음
- DB에는 표의 SHA-256(16진수 64자)만 저장
- 쿠키: HttpOnly(스크립트가 못 읽음) · SameSite=Lax · https면 Secure · 30일
- 메서드: newToken() · hash(token) · tokenFrom(cookieHeader) · cookie(token, maxAgeSeconds, secure)

### KeepLoginService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/keep/KeepLoginService.java
- 🔑 로그인 유지
- 왜 필요한가
- 로그인 상태(세션)는 서버 메모리에만 있음 → 배포로 서버가 다시 켜지면 모두 로그아웃됨, 30분 동안 아무것도 안 해도 로그아웃됨
- → 로그인할 때 기기에 "표"(쿠키 SKL_KEEP, 30일)를 하나 더 주고, 세션이 없으면 그 표로 다시 로그인 상태를 만들어 줌
- 흐름
- 메서드: issue(userId, response) · restore(request, response) · revoke(request, response) · revokeAll(userId) · revokeAllByEmail(email)

### IUserFindService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/IUserFindService.java
- 아이디 찾기 / 비밀번호 재설정 Service

### IUserLoginService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/IUserLoginService.java
- 로그인 화면 Service
- "무슨 기능이 있는지" 목록만 적는 곳
- 실제 동작은 UserLoginServiceImpl에서 구현

### IUserMyPageService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/IUserMyPageService.java
- 마이페이지 Service
- 검사에 걸리면 IllegalArgumentException(화면에 보여줄 메시지)을 던짐

### IUserOAuthService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/IUserOAuthService.java
- 소셜 로그인 화면 Service
- "무슨 기능이 있는지" 목록만 적는 곳
- 실제 동작은 UserOAuthServiceImpl에서 구현

### IUserSignUpService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/IUserSignUpService.java
- 회원가입 화면 Service
- "무슨 기능이 있는지" 목록만 적는 곳
- 실제 동작은 UserSignUpServiceImpl에서 구현

### UserFindServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/UserFindServiceImpl.java
- 아이디 찾기 / 비밀번호 재설정 Service 구현
- 보안
- 가입되지 않은 이메일도 "보냈습니다"로 똑같이 응답 → 가입 여부를 떠볼 수 없음
- 소셜 가입 계정은 비밀번호가 없으므로 코드 대신 "OO 로그인으로 가입된 계정" 안내 메일
- 코드: 6자리, 5분 만료, 1분 내 재발송 금지
- 메서드: findIds(nickname) · sendResetCode(email) · verifyResetCode(email, code) · resetPassword(email, rawPassword)

### UserLoginServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/UserLoginServiceImpl.java
- 로그인 화면 Service 구현
- 이메일로 회원을 찾고, 비밀번호가 맞는지 확인하는 곳
- 메서드: login(vo)

### UserMyPageServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/UserMyPageServiceImpl.java
- 마이페이지 Service 구현
- 비밀번호는 회원가입과 같은 BCrypt로 확인/저장
- 소셜 로그인(카카오/네이버) 회원은 비밀번호가 없으므로 비밀번호 변경 불가, 탈퇴는 확인 문구만
- 탈퇴는 "회원 정보 지우기 + 관심 종목 삭제"를 한 트랜잭션으로 (둘 중 하나만 되는 일 없게)
- 메서드: getMyInfo(id) · changeNickname(id, nickname) · changePassword(id, currentPassword, newPassword) · withdraw(id, currentPassword, confirmText) · getMyChats(id, page, size) · countMyChats(id)

### UserOAuthServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/UserOAuthServiceImpl.java
- 소셜 로그인 화면 Service 구현
- 네이버/카카오와 대화(토큰 받기, 회원 정보 받기)하고, 우리 회원으로 연결하는 곳
- 회원 정보를 받은 뒤 처리(기존 회원 / 이메일 중복 / 자동 가입)는 둘이 똑같아서 findOrCreateUser()로 공통 처리
- 메서드: getNaverLoginUrl(state) · loginWithNaver(code, state) · getKakaoLoginUrl(state) · loginWithKakao(code)

### UserSignUpServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/service/UserSignUpServiceImpl.java
- 메서드: isEmailDuplicated(vo) · sendVerifyCode(vo) · verifyCode(vo) · signUp(vo)

### UserFindVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/vo/UserFindVO.java
- 아이디 찾기 / 비밀번호 재설정 화면 VO
- 사용 테이블: T_USER, T_EMAIL_VERIFY

### UserLoginVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/vo/UserLoginVO.java
- 로그인 화면에서 쓰는 값을 담는 VO
- 화면 1개 = VO 1개
- 사용 테이블: T_USER
- 로그인 성공 후에는 이 객체가 세션에 저장됨 (비밀번호는 지우고 저장)

### UserMyPageVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/vo/UserMyPageVO.java
- 마이페이지 화면에서 쓰는 값을 담는 VO
- 화면 1개 = VO 1개
- 사용 테이블: T_USER, T_WATCHLIST(탈퇴 시 삭제), T_AI_CHAT_LOG(내 AI 질문 기록)

### UserOAuthVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/vo/UserOAuthVO.java
- 소셜 로그인(네이버/카카오) 화면에서 쓰는 값을 담는 VO
- 화면 1개 = VO 1개
- 사용 테이블: T_USER

### UserSignUpVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/user/vo/UserSignUpVO.java
- 회원가입 화면에서 쓰는 값을 담는 VO
- 화면 1개 = VO 1개
- 사용 테이블: T_USER, T_EMAIL_VERIFY
- @Getter/@Setter: Lombok이 getEmail(), setEmail() 같은 메서드를 자동으로 만들어줌
