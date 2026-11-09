package com.sklabs1037.sklabs1037.demo.service;

import com.sklabs1037.sklabs1037.demo.dao.DemoDao;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 🧪 체험 계정 (로그인 없이 체험하기)
 *
 * - 모두가 같은 계정(T_USER.PROVIDER = 'DEMO') 하나로 들어옴 → 회원가입 없이 핵심 기능을 바로 써 봄
 * - 세션에 DEMO = true 표시 → DemoGuard가 쓰기 요청을 허용 목록(ALLOWED)만 통과시킴
 *   (토론 글 · 메시지 · 문의 · 커플 · 닉네임·비밀번호 · 탈퇴 · 푸시 구독 같은 건 막음 → 남에게 보이는 흔적·악용 방지)
 * - AI 하루 사용 횟수도 이 계정 하나로 같이 셈 → 체험이 AI 비용을 크게 늘리지 않음 (+ 💸 AI 비용 한도)
 * - 매일 05:10 처음 상태로 (작업 DEMO_RESET)
 */
@Service
public class DemoService {

    public static final String SESSION_KEY = "DEMO";

    // 체험 계정이 쓰기(POST·PUT·DELETE)를 할 수 있는 주소 (앞부분이 같으면 허용)
    static final List<String> ALLOWED = List.of(
            "/api/user/logout",
            // 체험하다가 진짜 계정으로 로그인 · 회원가입 · 비밀번호 찾기 · 체험 다시 시작은 막으면 안 됨
            "/api/user/login", "/api/user/signup", "/api/user/find/", "/api/user/demo",
            "/api/mock/order", "/api/mock/orders/", "/api/mock/coach", "/api/mock/diagnosis",
            "/api/ai/",
            "/api/beauty/",
            "/api/predict/",
            "/api/quiz/",
            "/api/persona/",
            "/api/watchlist",
            "/api/alerts",
            "/api/me/notifications/read");

    @Autowired
    private DemoDao demoDao;

    @Autowired
    private IMockTradeService mockTradeService;

    // 체험 계정으로 들어가기 → 세션에 넣을 회원 정보
    public UserLoginVO loginUser() {

        Map<String, Object> u = demoDao.selectDemoUser();
        if (u == null) throw new IllegalStateException("체험 계정이 아직 준비되지 않았어요. 잠시 후 다시 시도해주세요.");

        UserLoginVO vo = new UserLoginVO();
        vo.setId(((Number) u.get("id")).longValue());
        vo.setEmail(String.valueOf(u.get("email")));
        vo.setNickname(String.valueOf(u.get("nickname")));
        vo.setRole("USER");
        return vo;

    }

    // 매일 05:10 처음 상태로 (OpsJobs "DEMO_RESET")
    public JobOutcome resetDaily() {

        Map<String, Object> u = demoDao.selectDemoUser();
        if (u == null) return JobOutcome.of("체험 계정 없음 (sql/demo.sql) → 건너뜀");

        Long id = ((Number) u.get("id")).longValue();
        mockTradeService.reset(id);
        int n = demoDao.deleteNotifications(id);
        int a = demoDao.deletePriceAlerts(id);
        return JobOutcome.of(1, 0, "모의투자 계좌 처음으로 · 알림 " + n + "개 · 가격 알림 " + a + "개 지움");

    }

    // 쓰기 요청 허용? (순수 계산, 테스트 가능)
    public static boolean allowed(String method, String uri) {

        if (method == null || uri == null) return false;
        String m = method.toUpperCase();
        if (m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS")) return true;
        if (uri.contains("..")) return false;
        return ALLOWED.stream().anyMatch(uri::startsWith);

    }
}
