package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /api/ai-analysis 응답: {"analysis": {...} 또는 null, "reviews": [...], "error": "..."} */
public class AiAnalysisResponse {
    public Analysis analysis;
    public List<Review> reviews;
    public String error;

    /** AI가 실사용 후기와 뉴스를 읽고 만든 구매 리포트 */
    public static class Analysis {
        public double score;            // 1.0 ~ 5.0
        public String summary;          // 종합 한 줄 평
        public List<String> pros;       // 장점
        public List<String> cons;       // 단점
        public List<String> cautions;   // 구매 시 주의할 점
        public List<String> defects;    // 여러 후기에서 반복된 결함 (없으면 빈 배열)
        public boolean recall;          // 뉴스에 리콜 언급이 있으면 true

        @SerializedName("suitable_for")
        public String suitableFor;      // 이런 사람에게 추천
    }

    /** 블로그 · 카페 사용 후기 한 건 */
    public static class Review {
        public String source;   // "블로그" | "카페"
        public String title;
        public String review;   // 본문 앞부분 (최대 200자)
        public String date;     // yyyyMMdd
        public String link;
    }
}
