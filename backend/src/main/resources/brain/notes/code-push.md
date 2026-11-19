key: code-push
title: Spring 코드 · push 패키지
topics: spring, push
---
## 요약
📲 휴대폰·PC 푸시 알림

클래스 4개 · API 5개 · DB 표 2개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/push`

## API
- GET /api/push/key → key()
- GET /api/push/devices → devices()
- POST /api/push/subscribe → subscribe()
- POST /api/push/unsubscribe → unsubscribe()
- POST /api/push/test → test()

## DB 표
- T_PUSH_KEY, T_PUSH_SUB

## 클래스

### PushController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/push/controller/PushController.java
- 📲 휴대폰·PC 푸시 알림

### PushDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/push/dao/PushDao.java
- 📲 푸시 알림 DAO (SQL: resources/mapper/PushMapper.xml)
- 메서드: selectKey · insertKey · upsertSub · deleteSub · deleteByEndpoint · countByUser · selectSubs · selectMyDevices · markOk · markFail

### PushService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/push/service/PushService.java
- 📲 휴대폰·PC 푸시 알림
- 흐름
- 1. 화면: 🔔 알림 창의 "휴대폰·PC 알림 켜기" → 브라우저가 허락을 묻고 → 구독 정보(ENDPOINT, 키 2개)를 서버에 저장
- 2. NotificationService.notify 로 사이트 알림이 새로 생기면 → 그 사람의 기기마다 푸시 (따로 스레드, 원래 일은 안 기다림)
- 3. 서비스 워커(public/sw.js)가 받아서 알림을 띄우고, 누르면 그 화면으로
- 메서드: publicKey() · subscribe(userId, endpoint, p256dh, auth, userAgent) · unsubscribe(userId, endpoint) · devices(userId) · sendTest(userId) · send(userId, kind, title, body, link)

### WebPushCrypto · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/push/service/WebPushCrypto.java
- 📲 웹 푸시 암호화 (외부 라이브러리 없이 JDK만)
- VAPID (RFC 8292): 서버 키(P-256)로 서명한 JWT(ES256) → 푸시 서비스가 "이 사이트가 보낸 것"인지 확인
- 내용 암호화 (RFC 8291, aes128gcm): 브라우저 공개키와 ECDH → HKDF → AES-128-GCM
- → 푸시 서비스(구글·애플·모질라)는 알림 내용을 읽을 수 없음
- RFC 8291 5장 예시 값으로 테스트 (WebPushCryptoTest)
