package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * B2B 제품 시장 분석 응답 (단계 4, 승인된 사업자 회원 전용).
 *   GET /api/b2b/product-search?q=           → ProductSearch
 *   GET /api/b2b/search-analysis?q=&hint=    → Analysis
 * 서버 값이 없으면 null로 오는 숫자는 Double/Integer로 받는다.
 */
public final class MarketModels {

    private MarketModels() { }

    // ── 제품 검색 ──────────────────────────────────────────────
    public static class ProductSearch {
        public String query;
        public List<SearchItem> items;
        // 다나와 장애 때 저장된 결과로 응답하면 붙는다 (정상이면 null)
        public Boolean stale;
        @SerializedName("stale_at") public String staleAt;
    }

    public static class SearchItem {
        public String title;
        public String brand;
        public Double price;
        public String image;
        public String link;
        @SerializedName("energy_grade") public String energyGrade;            // "1" (숫자로 와도 문자열로 받음)
        @SerializedName("monthly_cost") public Double monthlyCost;            // 한 달 전기요금(원)
        @SerializedName("monthly_cost_basis") public String monthlyCostBasis; // "하루 8시간 기준" (공기청정기만)
        // 정렬용 (다나와 검색일 때만 있고 네이버면 null). 형식이 바뀌어도 화면이 깨지지 않게 문자열로 받는다
        public String release;                                                // 출시(제조)년월 "2025-06" → 최신순
        @SerializedName("review_count") public String reviewCount;            // 리뷰 수 → 인기순
        @SerializedName("review_score") public String reviewScore;            // 평점 0~5
    }

    // ── 분석 ──────────────────────────────────────────────────
    // 화면은 실제 데이터 5개 카드만 쓴다 (웹 B2BAnalysis와 같음). 응답에 있는 AI 리포트 · 가격 적정성 ·
    // 가격 예측 · price_position 같은 판단 · 예측 값은 받지 않는다 (Gson은 모르는 키를 무시)
    public static class Analysis {
        /** 제품명 · 검색어에서 품목(냉장고 등)을 못 찾으면 true + message만 온다 (분석 안 함) */
        public boolean unsupported;
        public String message;
        public String query;
        public String category;
        @SerializedName("model_number") public String modelNumber;
        @SerializedName("trend_keyword") public String trendKeyword;
        @SerializedName("product_info") public ProductInfo productInfo;
        @SerializedName("similar_products") public List<SimilarProduct> similarProducts;
        @SerializedName("competitor_comparison") public Comparison competitorComparison;
        @SerializedName("interest_forecast") public Interest interestForecast;
        @SerializedName("review_sentiment") public ReviewSentiment reviewSentiment;
    }

    public static class ProductInfo {
        public String name;
        public String brand;
        public String image;
        @SerializedName("release_date") public String releaseDate;      // 다나와 등록월 "2025-06"
        @SerializedName("launch_price") public Double launchPrice;      // 진짜 출시가만, 없으면 null
        @SerializedName("max_price_24m") public Double maxPrice24m;     // 출시가가 없을 때 대신: 다나와 최근 24개월 최고가
        @SerializedName("current_price") public Double currentPrice;
    }

    /** 비슷한 제품 (다른 브랜드 · 같은 용량대, 없으면 비슷한 가격대) 최대 5개 */
    public static class SimilarProduct {
        public String title;
        public String brand;
        public Double price;
        public String image;
        public String link;
        @SerializedName("model_number") public String modelNumber;
        public String capacity;   // 용량(L) 숫자. 같은 용량대로 고른 경우에만 있다
    }

    /** 장단점은 실사용 후기를 요약한 것. 응답의 recommended · recommendation_reason(추천 문구)은 쓰지 않는다 */
    public static class Comparison {
        public Competitor competitor;
        @SerializedName("selected_pros") public List<String> selectedPros;
        @SerializedName("selected_cons") public List<String> selectedCons;
        @SerializedName("competitor_pros") public List<String> competitorPros;
        @SerializedName("competitor_cons") public List<String> competitorCons;
    }

    public static class Competitor {
        public String title;
        public String brand;
        public Double price;
        public String image;
        public String link;
        @SerializedName("model_number") public String modelNumber;
        @SerializedName("matched_capacity") public String matchedCapacity;   // 같은 용량으로 맞춘 경우 용량(L)
    }

    /** 검색량 추이: 네이버 데이터랩 실제 값만 쓴다 (예측 · 추세선 키는 받지 않음) */
    public static class Interest {
        public List<TrendPoint> history;
    }

    public static class TrendPoint {
        public String period;   // "2026-11-16" (주 시작일)
        public Double ratio;    // 기간 최댓값 = 100인 상대값
    }

    public static class ReviewSentiment {
        public List<Review> reviews;
    }

    public static class Review {
        public String source;   // 블로그 · 카페
        public String title;
        public String text;
        public String link;     // 원문
    }
}
