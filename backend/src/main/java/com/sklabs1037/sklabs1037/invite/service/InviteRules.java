package com.sklabs1037.sklabs1037.invite.service;

/**
 * 🤝 친구 초대 규칙 (순수 계산)
 * - 초대 코드 = (회원 번호 × 7919 + 1037)을 36진수로 → 따로 저장하지 않아도 코드 ↔ 회원 번호를 오갈 수 있음
 *   (아무 글자나 넣으면 대부분 맞지 않아서 걸러짐. 비밀은 아님 — 초대는 배지만 주니까)
 * - 초대는 "가입한 지 3일 안"인 사람만 받을 수 있음 (오래된 회원끼리 서로 눌러 배지를 받는 것 방지)
 */
public final class InviteRules {

    static final long MUL = 7919L;
    static final long ADD = 1037L;
    public static final long ACCEPT_MS = 3L * 24 * 3600_000;
    public static final int MANY = 5;   // 이만큼 초대하면 배지 하나 더

    private InviteRules() {
    }

    public static String code(long userId) {

        if (userId <= 0) throw new IllegalArgumentException("회원 번호가 이상해요.");
        return Long.toString(userId * MUL + ADD, 36).toUpperCase();

    }

    // 코드 → 회원 번호 (맞지 않는 코드면 null)
    public static Long userId(String raw) {

        if (raw == null) return null;
        String code = raw.strip();
        if (!code.matches("[0-9A-Za-z]{2,12}")) return null;
        try {
            long n = Long.parseLong(code, 36) - ADD;
            return n > 0 && n % MUL == 0 ? Long.valueOf(n / MUL) : null;
        } catch (NumberFormatException e) {
            return null;
        }

    }

    // 초대를 받을 수 있는지 → 안 되는 이유 (되면 null)
    public static String refuse(Long inviterId, long me, String myProvider, Long joinedAt, long now) {

        if (inviterId == null) return "초대 코드가 맞지 않아요.";
        if (inviterId == me) return "내 초대 링크로는 받을 수 없어요.";
        if ("DEMO".equals(myProvider) || "AI".equals(myProvider)) return "체험 계정은 초대를 받을 수 없어요.";
        if (joinedAt == null || now - joinedAt > ACCEPT_MS) return "가입한 지 3일 안에만 초대를 받을 수 있어요.";
        return null;

    }
}
