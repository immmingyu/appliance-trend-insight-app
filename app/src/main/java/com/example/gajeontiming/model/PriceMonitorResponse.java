package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GET /api/b2b/price-monitor 응답 (승인된 사업자 회원 전용, B2B 단계 3).
 * 카테고리별 평균가 변동과 흐름(하락 · 상승 · 보합). 서버 DB 스냅샷 기준이고, 스냅샷이 없는 카테고리는 실시간 조회(realtime)로 채운다.
 */
public class PriceMonitorResponse {
    public Summary summary;
    public List<Category> categories;
    @SerializedName("updated_at") public String updatedAt;   // "2026-11-22"

    public static class Summary {
        public int total;
        @SerializedName("drop_count") public int dropCount;      // 하락 카테고리 수
        @SerializedName("rise_count") public int riseCount;      // 상승 카테고리 수
        // buy_signals는 서버가 항상 빈 배열로 보내서 받지 않는다 (매입 판단 없음)
        @SerializedName("has_realtime") public boolean hasRealtime;
    }

    public static class Category {
        public String category;
        @SerializedName("avg_price") public long avgPrice;
        @SerializedName("min_price") public long minPrice;
        @SerializedName("day_change_pct") public Double dayChangePct;    // 전일 대비 (믿을 수 없으면 null)
        @SerializedName("week_change_pct") public Double weekChangePct;  // 7일 대비 (믿을 수 없으면 null)
        @SerializedName("brand_changes") public List<BrandChange> brandChanges;
        @SerializedName("last_updated") public String lastUpdated;
        public boolean realtime;                                         // DB 스냅샷 없이 지금 조회한 값
        public String signal;                                            // 하락 · 상승 · 보합 · 데이터 부족 · 실시간 조회 (화면 문구)
        @SerializedName("signal_type") public String signalType;         // down · up · neutral · realtime (7일 평균가 ±2%)
    }

    public static class BrandChange {
        public String brand;
        @SerializedName("change_pct") public double changePct;
        @SerializedName("current_avg") public long currentAvg;
    }
}
