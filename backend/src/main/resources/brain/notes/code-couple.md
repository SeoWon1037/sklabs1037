key: code-couple
title: Spring 코드 · couple 패키지
topics: spring, couple
---
## 요약
💕 커플 맺기 (모두 로그인)

클래스 5개 · API 12개 · DB 표 7개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/couple`

## API
- GET /api/couple/photos → album()
- POST /api/couple/photos → upload()
- POST /api/couple/photos/{photoId}/delete → deletePhoto()
- GET /api/couple/photos/{photoId}/image → image()
- GET /api/couple → mine()
- GET /api/couple/summary → summary()
- POST /api/couple/request → request()
- POST /api/couple/{id}/{action} → act()
- POST /api/couple/heart → heart()
- POST /api/couple/note → note()
- POST /api/couple/birthday → birthday()
- POST /api/couple/end → end()

## DB 표
- T_COUPLE, T_COUPLE_BIRTHDAY, T_COUPLE_HEART, T_COUPLE_NOTE, T_COUPLE_PHOTO, T_NOTIFICATION, T_USER

## 클래스

### CoupleController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/couple/controller/CoupleController.java
- 💕 커플 맺기 (모두 로그인)

### CoupleDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/couple/dao/CoupleDao.java
- 💕 커플 맺기 DAO (SQL: resources/mapper/CoupleMapper.xml)
- 메서드: selectUsersByNickname · selectActive · selectSent · selectReceived · selectCouple · countActive · countPendingBetween · countSentPending · insertRequest · accept · decline · cancel · end · cancelOthers · insertHeart · selectHeartCounts · deleteUnreadHeartNotifications · selectBirthdays · upsertBirthday · deleteBirthday · selectBirthdaysOn · insertNote · selectNotes · countNotesToday · selectActiveCouples · insertPhoto · selectPhotos · selectPhotoData · countPhotos · deletePhoto

### BirthdayRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/couple/service/BirthdayRules.java
- 🎂 커플 생일 규칙 (순수 계산 → 테스트 가능)
- 월 · 일만 다룸 (양력). 2월 29일생은 윤년이 아닌 해에는 2월 28일에 챙김
- dDay: 오늘이 생일이면 0, 지났으면 내년 생일까지
- 메서드: monthDay(month, day) · inYear(md, year) · next(md, today) · dDay(md, today) · view(name, md, today) · text(name, md, today) · Message(title, body) · anniversaryMessage(label, date, daysLeft) · partnerMessage(name, md, daysLeft)

### CouplePhotoService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/couple/service/CouplePhotoService.java
- 📸 커플 기념일 앨범
- 지난 기념일(100일 단위 · 주년)마다 사진 칸이 열림, 아무 때나 올리는 "추억" 칸도 있음
- 다음 기념일은 🔒 (그날이 되면 열림)
- 사진은 화면에서 긴 변 1600px JPEG로 줄여서 보냄 → 서버는 그림 파일인지(JPEG/PNG/WebP) · 3MB 이하인지만 확인
- 두 사람만 볼 수 있음 (그림 주소도 로그인 + 같은 커플인지 확인), 한 칸에 6장 · 커플당 200장
- 메서드: album(userId) · upload(userId, milestone, caption, image) · delete(userId, photoId) · image(userId, photoId) · imageType(b) · decode(image)

### CoupleService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/couple/service/CoupleService.java
- 💕 커플 맺기
- 흐름
- 1. 신청: 상대 닉네임 + 시작일(신청한 사람이 정함, 오늘이나 과거) → 🔔 상대에게 알림
- 2. 상대가 수락하면 커플 (둘 다 다른 커플이 없을 때만) / 거절 · 신청 취소도 가능
- 3. 로그인하면 "○○님과 200일 ♥" (시작일 = 1일, 우리나라에서 세는 방식) + 다가오는 기념일 D-day
- 메서드: mine(userId) · summary(userId) · request(userId, nickname, startDate) · accept(userId, id) · decline(userId, id) · cancel(userId, id) · end(userId) · sendNote(userId, text) · setBirthday(userId, who, month, day) · partner(userId) · info(userId) · birthdayRemindAll() · heart(userId)
