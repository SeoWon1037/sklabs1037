key: code-plan
title: Spring 코드 · plan 패키지
topics: spring, plan
---
## 요약
🗓️ 내 약속 + 🔊 음성 브리핑 Controller (모두 로그인 필요)

클래스 7개 · API 10개 · DB 표 8개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/plan`

## API
- GET /api/plans → list()
- POST /api/plans → add()
- POST /api/plans/{id}/delete → delete()
- GET /api/plans/past → past()
- POST /api/plans/{id}/photos → addPhoto()
- POST /api/plans/photos/{photoId}/delete → deletePhoto()
- GET /api/plans/photos/{photoId}/image → photo()
- GET /api/voice/settings → settings()
- POST /api/voice/settings → save()
- GET /api/voice/brief → brief()

## DB 표
- T_NEWS, T_PLAN, T_PLAN_PHOTO, T_STOCK, T_STOCK_PRICE, T_USER, T_VOICE_BRIEF, T_WATCHLIST

## 클래스

### PlanController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/controller/PlanController.java
- 🗓️ 내 약속 + 🔊 음성 브리핑 Controller (모두 로그인 필요)

### PlanDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/dao/PlanDao.java
- 🗓️ 내 약속 + 🔊 음성 브리핑 설정 DAO (SQL: resources/mapper/PlanMapper.xml)
- 메서드: insertPlan · countUpcoming · selectMine · deleteRepeat · updateRepeatId · selectWatchNews · deletePlan · selectAllBetween · selectPlan · insertPhoto · countPhotos · selectPhotos · selectPhotoData · deletePhoto · deletePhotosOfPlan · deleteOrphanPhotos · deleteOld · selectSetting · upsertSetting · selectMorningUsers · selectCloseUsers · selectNickname · selectWatchPrev

### PlanVoiceScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/scheduler/PlanVoiceScheduler.java
- 🗓️ 약속 알림 · 🔊 음성 브리핑 스케줄러 (한국 시간)
- 07:00 매일      VOICE_MORNING  아침 브리핑 (날씨 + 오늘 약속)
- 09:00 매일      PLAN_REMIND    약속 알림 (하루 전 · 당일)
- 09:00 매일      COUPLE_BIRTHDAY 커플 생일 알림 (7일 전 · 하루 전 · 당일)
- 15:40 평일      VOICE_CLOSE    장 마감 브리핑 (관심 종목, 휴장일은 건너뜀)

### PlanRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/service/PlanRules.java
- 🗓️ 내 약속 규칙 (순수 계산 → 테스트 가능)
- 날짜 · 시간은 한국 시간 그대로 다룸 (서버가 어느 시간대에 있든 호출하는 쪽이 한국 시간 now를 넘김)
- 시간이 00:00이면 "시간 미정" (말할 때 시간을 빼고 말함)
- 말로 읽을 문장을 만드는 곳: "내일 저녁 7시 강남에서 민지랑 저녁"
- 메서드: title(raw) · place(raw) · when(rawDate, rawTime, now) · weekly(first, weeks) · yes(raw) · timeText(t) · dayText(d, today) · weekday(w) · line(at, title, place) · Message(title, body) · reminder(List<Map<String, plans, dayWord) · speech(List<Map<String, plans, dayWord)

### PlanService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/service/PlanService.java
- 🗓️ 내 약속
- 추가: 내 약속 화면(/plans) 또는 AI 챗봇("토요일 저녁 7시 강남에서 민지랑 약속 잡아줘" → add_plan 도구)
- 💕 함께(shared): 커플인 상대의 목록 · 알림에도 나옴. 만들 때 상대에게 바로 알림. 지울 수 있는 사람은 만든 사람
- 🔁 매주 반복: 12주치를 미리 여러 줄로 만들어 둠(REPEAT_ID로 묶음) → 목록 · 알림 코드는 반복을 몰라도 됨
- 📷 사진: 약속마다 4장 (T_PLAN_PHOTO), 볼 수 있는 사람 = 만든 사람 + 함께하는 약속의 상대, 약속을 지우면 같이 지움
- 메서드: add(userId, title, date, time, place) · add(userId, title, date, time, place, shared, weekly) · upcoming(userId) · pastWithPhotos(userId) · addPhoto(userId, planId, image) · deletePhoto(userId, photoId) · photo(userId, photoId) · delete(userId, id, all) · delete(userId, id) · speech(userId) · todaySpeech(userId) · remindAll()

### VoiceBriefRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/service/VoiceBriefRules.java
- 🔊 음성 브리핑 문장 만들기 (순수 계산 → 테스트 가능)
- AI를 쓰지 않음: 날씨 숫자 · 내 약속 · 관심 종목 등락을 정해진 문장에 넣기만 함 (지어낼 틈이 없음, 비용 0)
- 교통: 실시간 소통 정보는 아직 연결하지 않음. 비 · 눈 예보가 있을 때만 "평소보다 막힐 수 있어요"라고 말함 (예측이 아니라 주의)
- 모의투자 내용은 넣지 않음 (서원 요청)
- 메서드: Region(code, name, lat, lon) · region(code) · regionByName(text) · Weather(code, min, max, rainProb, now, pm10) · sky(code) · dust(pm10) · weatherLines(regionName, w) · morning(nickname, regionName, w, planSpeech) · morning(nickname, regionName, w, planSpeech, newsStock, newsTitle) · changes(List<Map<String, rows) · close(nickname, List<Map<String, changes) · rateText(rate)

### VoiceBriefService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/plan/service/VoiceBriefService.java
- 🔊 음성 브리핑
- 아침 (작업 VOICE_MORNING, 매일 07:00): 오늘 날씨(지역 선택) + 오늘 약속 + 관심 종목 뉴스 제목 한 개 → 알림 + 📲 푸시
- 장 마감 (작업 VOICE_CLOSE, 거래일 15:40): 내 관심 종목이 오늘 얼마나 움직였는지 → 알림 + 📲 푸시 (모의투자 내용은 넣지 않음)
- 알림을 누르면 /ai?brief=morning|close|plan → GET /api/voice/brief 로 "지금 기준" 문장을 다시 만들어 소리로 읽어 줌
- (웹사이트는 꺼져 있을 때 스스로 소리를 낼 수 없어서, 알림 → 한 번 누르기 → 읽기 순서)
- 메서드: settings(userId) · save(userId, morning, close, region) · brief(userId, kind) · weather(region) · weatherFor(regionText) · morningAll() · closeAll()
