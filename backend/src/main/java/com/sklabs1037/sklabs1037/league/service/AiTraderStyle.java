package com.sklabs1037.sklabs1037.league.service;

/**
 * AI 투자자 성격 (T_AI_TRADER.CODE)
 *
 * - persona   : AI에게 주는 투자 철학 (프롬프트)
 * - 숫자 규칙 : AI가 뭐라고 하든 서버가 지킴 (종목 수·한 종목 최대 비중·최소 현금·하루 주문 수)
 *   → "AI는 판단, 규칙은 서버" (AI가 몰빵하거나 현금을 다 쓰지 못하게)
 */
public enum AiTraderStyle {

    NEWS("뉴스헌터",
            "어제·오늘 뉴스에서 호재가 나온 종목을 빠르게 사고, 악재가 나오거나 재료가 식으면 판다. "
                    + "뉴스가 없는 종목은 건드리지 않는다. 3~6종목에 집중한다.",
            6, 25, 5, 5, true),

    MOMENTUM("추세추종",
            "최근 5일·20일 수익률이 강한 종목을 따라 산다. 5일 수익률이 음수로 꺾이거나 -7% 손실이면 판다. "
                    + "떨어지는 종목의 반등을 기대하고 사지 않는다. 최대 5종목.",
            5, 25, 5, 5, false),

    STEADY("안정분산",
            "시가총액 상위·변동성이 낮은 종목에 6~10종목으로 나눠 담고 오래 들고 간다. "
                    + "한 종목이 너무 커지면 줄이고(리밸런싱), 자주 사고팔지 않는다. 할 게 없으면 아무것도 안 해도 된다.",
            10, 15, 10, 4, false);

    public final String label;
    public final String persona;
    public final int maxPositions;     // 최대 보유 종목 수
    public final int maxWeightPct;     // 한 종목 최대 비중 (총자산 대비 %)
    public final int minCashPct;       // 최소 현금 비중 (%)
    public final int maxOrders;        // 하루 최대 주문 수
    public final boolean newsHeavy;    // 종목 뉴스를 많이 볼지

    AiTraderStyle(String label, String persona, int maxPositions, int maxWeightPct, int minCashPct, int maxOrders, boolean newsHeavy) {
        this.label = label;
        this.persona = persona;
        this.maxPositions = maxPositions;
        this.maxWeightPct = maxWeightPct;
        this.minCashPct = minCashPct;
        this.maxOrders = maxOrders;
        this.newsHeavy = newsHeavy;
    }

    public static AiTraderStyle of(String code) {

        try {
            return code == null ? null : valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }

    }
}
