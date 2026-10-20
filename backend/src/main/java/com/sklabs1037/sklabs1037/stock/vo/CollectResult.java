package com.sklabs1037.sklabs1037.stock.vo;

/**
 * 여러 종목 시세 수집 결과
 * - stocks: 대상 종목 수 / saved: 저장한 일봉 건수 / failed: 실패한 종목 수 / firstError: 첫 실패 이유 (운영 화면·메일용)
 */
public record CollectResult(int stocks, int saved, int failed, String firstError) {

    public static final CollectResult EMPTY = new CollectResult(0, 0, 0, null);

    // 운영 기록용 한 줄 요약
    public String summary() {

        String text = "종목 " + stocks + "개, 저장 " + saved + "건, 실패 " + failed + "종목";

        return firstError == null ? text : text + " (첫 실패: " + firstError + ")";

    }
}
