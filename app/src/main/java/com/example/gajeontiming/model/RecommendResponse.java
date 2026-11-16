package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /api/recommend 응답: AI가 고른 제품 3개 */
public class RecommendResponse {
    public List<RecommendItem> recommendations;

    @SerializedName("search_term")
    public String searchTerm;      // AI가 문장에서 뽑아낸 검색어 (예: "벽걸이 에어컨")

    public String error;           // 못 찾았거나 예산이 너무 낮을 때 사용자에게 보여줄 문장

    @SerializedName("ai_limited")
    public boolean aiLimited;      // AI 사용량이 몰려 리뷰 점수 순으로만 고른 경우 true

    /** 추천 제품 하나: 공통 제품 필드 + 추천 이유 */
    public static class RecommendItem extends Product {
        public String reason;      // 이 제품을 추천하는 이유 1~2문장
        public String highlight;   // 핵심 장점 2~4단어
    }
}
