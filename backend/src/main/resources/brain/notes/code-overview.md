key: code-overview
title: Spring 코드 · 전체 구조
topics: spring, 구조
links: code-admin, code-ai, code-alert, code-autofix, code-beauty, code-brain, code-brief, code-calendar, code-client, code-couple, code-demo, code-dm, code-duel, code-fx, code-idea, code-invite, code-league, code-market, code-me, code-mock, code-news, code-og, code-ops, code-persona, code-plan, code-predict, code-push, code-qa, code-quiz, code-stock, code-support, code-translate, code-user, code-whatif
---
## 요약
SKLabs 백엔드(Spring Boot 4 · Java 17 · MyBatis · PostgreSQL)의 패키지 지도. 패키지마다 "Spring 코드 · ○○ 패키지" 노트가 따로 있어요.

## 구조 규칙
- controller → service → dao(MyBatis XML, 결과는 LinkedHashMap · AS "camelCase")
- ○○Rules: 순수 계산(스프링 없이 테스트) / ○○Service: DB · 외부 API를 묶음
- 설계 원칙: AI는 정리, 판단은 서버 규칙 · 되돌릴 수 없는 일은 사람 승인
- DB 바꾸기: resources/db/auto/날짜-순번-설명.sql (IF NOT EXISTS, 서버가 켜질 때 한 번 실행) + ops/service/SchemaExpect 등록

## 패키지
- **admin** — 관리자 피드백 대시보드 Controller (클래스 5 · API 3)
- **ai** — AI 챗봇 Controller (클래스 14 · API 7)
- **alert** — 목표가 알림 Controller (로그인한 사람만, 본인 알림만) (클래스 6 · API 4)
- **autofix** — 🤖 AI 자동 수정 (클래스 6 · API 7)
- **beauty** — 뷰티 랩 Controller (클래스 16 · API 13)
- **brain** — 🧠 세컨드 브레인 Controller (모두 로그인 필요, 본인 노트만) (클래스 6 · API 10)
- **brief** — 아침 브리핑 메일 Controller (마이페이지) (클래스 5 · API 3)
- **calendar** — 📅 증시 일정 (로그인 없이 누구나) (클래스 5 · API 3)
- **client** — 💸 AI 하루 한도를 다 썼거나 관리자가 긴급 정지 → Claude를 부르지 않음 (클래스 9)
- **couple** — 💕 커플 맺기 (모두 로그인) (클래스 5 · API 12)
- **demo** — 🧪 로그인 없이 체험하기 (클래스 5 · API 1)
- **dm** — 💬 1:1 메시지 (전부 로그인) (클래스 4 · API 9)
- **duel** — ⚔️ 친구 대결 (클래스 4 · API 5)
- **fx** — 💱 오늘의 환율 (로그인 없이) (클래스 2 · API 1)
- **idea** — 💡 기능 제안 + 투표 (클래스 3 · API 6)
- **invite** — 🤝 친구 초대 (로그인 필요) (클래스 4 · API 2)
- **league** — 🤖 AI vs 사람 투자 리그 (클래스 6 · API 1)
- **market** — 🛍️ 중고거래 장터 (클래스 5 · API 27)
- **me** — 내 활동 · 알림 · 오늘의 SKLabs (모두 로그인) (클래스 10 · API 9)
- **mock** — 🩺 포트폴리오 AI 진단 (로그인) (클래스 17 · API 18)
- **news** — 메인 화면(뉴스 목록) Controller (클래스 15 · API 2)
- **og** — 🔗 공유용 주소 — 카카오톡 등에 붙이면 내용이 담긴 미리보기 카드가 뜨고, 누르면 진짜 화면으로 넘어감 (클래스 2 · API 2)
- **ops** — 💸 AI 비용 (관리자만) (클래스 37 · API 16)
- **persona** — 🧭 투자 성향 테스트 (클래스 2 · API 2)
- **plan** — 🗓️ 내 약속 + 🔊 음성 브리핑 Controller (모두 로그인 필요) (클래스 7 · API 10)
- **predict** — 🎯 오를까 내릴까 (클래스 5 · API 2)
- **push** — 📲 휴대폰·PC 푸시 알림 (클래스 4 · API 5)
- **qa** — 🤖 AI QA 봇 (클래스 5 · API 5)
- **quiz** — 📰 오늘의 뉴스 퀴즈 (클래스 4 · API 2)
- **stock** — 관리자: 토론방 신고 관리 (클래스 26 · API 22)
- **support** — 📮 문의함 (클래스 4 · API 7)
- **translate** — 🌐 번역 (로그인 없이 누구나) (클래스 3 · API 1)
- **user** — 아이디 찾기 / 비밀번호 재설정 Controller (클래스 29 · API 20)
- **whatif** — ⏳ "그때 샀더라면" (로그인 없이 누구나) (클래스 4 · API 1)
