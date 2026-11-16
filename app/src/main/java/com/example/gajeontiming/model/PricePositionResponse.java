package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

/** GET /api/price-position 응답: 같은 품목 전체 평균과 비교한 위치 */
public class PricePositionResponse {
    public boolean available;                       // 이 품목의 통계가 없으면 false
    @SerializedName("avg_price") public long avgPrice;
    @SerializedName("vs_avg_pct") public Double vsAvgPct;   // 품목 평균 대비 몇 % (음수면 저렴)
    public String signal;                           // 구매 추천 | 적정가 | 관망 권장
    @SerializedName("signal_type") public String signalType;   // buy | neutral | wait
    public String reason;
}
