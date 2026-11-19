key: code-alert
title: Spring 코드 · alert 패키지
topics: spring, alert
---
## 요약
목표가 알림 Controller (로그인한 사람만, 본인 알림만)

클래스 6개 · API 4개 · DB 표 3개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/alert`

## API
- GET /api/alerts → list()
- POST /api/alerts → create()
- DELETE /api/alerts/{alertId} → delete()
- POST /api/alerts/{alertId}/reactivate → reactivate()

## DB 표
- T_PRICE_ALERT, T_STOCK, T_USER

## 클래스

### PriceAlertController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/alert/controller/PriceAlertController.java
- 목표가 알림 Controller (로그인한 사람만, 본인 알림만)

### PriceAlertDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/alert/dao/PriceAlertDao.java
- 목표가 알림 Dao
- 메서드 이름 = PriceAlertMapper.xml의 id
- 메서드: selectMyAlerts · countActiveAlerts · insertAlert · deleteAlert · selectMyAlert · reactivateAlert · selectActiveAlerts · markSent · revertSent

### PriceAlertScheduler · Component
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/alert/scheduler/PriceAlertScheduler.java
- 목표가 알림 스케줄러
- 정규장(평일 09:00~15:30) 1분마다, 매분 30초에 확인 (지정가 체결 확인과 시각을 엇갈리게)

### IPriceAlertService · interface
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/alert/service/IPriceAlertService.java
- 목표가 알림 Service

### PriceAlertServiceImpl · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/alert/service/PriceAlertServiceImpl.java
- 목표가 알림 Service 구현
- 알림 흐름
- 1. 사용자가 "삼성전자 220,000원 이상" 같은 알림을 걸어둠 (ACTIVE)
- 2. 정규장 1분마다 PriceAlertScheduler → checkAndNotify()
- 켜진 알림이 없으면 토스도 호출하지 않음
- 메서드: getMyAlerts(userId, stockId) · create(userId, stockId, condition, targetPrice) · delete(userId, alertId) · reactivate(userId, alertId) · checkAndNotify()

### PriceAlertVO · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/alert/vo/PriceAlertVO.java
- 목표가 알림 VO
- 사용 테이블: T_PRICE_ALERT (+ T_STOCK, T_USER)
