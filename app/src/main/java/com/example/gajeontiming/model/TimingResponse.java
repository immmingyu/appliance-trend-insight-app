package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /api/timing 응답: 30일 가격 이력 + 서버가 계산한 구매 판단 */
public class TimingResponse {
    public List<PricePoint> data;                 // 날짜별 최저가 (오래된 날짜부터)

    @SerializedName("tracked_product")
    public TrackedProduct trackedProduct;         // 서버가 실제로 가격을 추적한 제품

    public boolean matched;                       // 누른 제품을 서버가 정확히 찾아 추적했는지
    public Analysis analysis;                     // 데이터가 부족하면 null
    public String error;

    public static class PricePoint {
        public String period;   // "2026-11-12"
        public long price;
    }

    public static class TrackedProduct {
        public String id;
        public String title;
        public String image;
        public long price;
        public String mallName;
    }

    public static class Analysis {
        @SerializedName("cur_min") public long curMin;           // 현재가
        @SerializedName("hist_avg") public long histAvg;         // 30일 평균
        @SerializedName("hist_min") public long histMin;         // 30일 최저
        @SerializedName("hist_max") public long histMax;         // 30일 최고
        @SerializedName("launch_price") public Long launchPrice;           // 출시가 (모르면 null)
        @SerializedName("launch_disc_pct") public Double launchDiscPct;    // 출시가 대비 할인율
        @SerializedName("diff_pct") public double diffPct;       // 평균 대비 현재가 차이(%)
        public String score;      // 최고 | 좋음 | 보통 | 대기
        public String color;      // green | blue | yellow | red
        public String message;    // 판단 설명 한 문장
        @SerializedName("trend_dir") public String trendDir;     // 상승 | 하락 | 안정
        @SerializedName("low_day") public String lowDay;         // 최저가였던 날
        @SerializedName("has_history") public boolean hasHistory; // 이력이 2일 이상인지
        @SerializedName("search_summary") public SearchSummary searchSummary;
    }

    /** 네이버 검색량으로 본 관심도 (검색 데이터가 7일 미만이면 null) */
    public static class SearchSummary {
        @SerializedName("diff_pct") public double diffPct;   // 최근 7일 관심도가 전체 평균보다 몇 % 높은지
        @SerializedName("peak_day") public String peakDay;   // 관심이 가장 높았던 날
        public String keyword;
    }
}
