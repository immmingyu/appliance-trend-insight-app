package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

/** 트렌드 목록의 제품 하나: 공통 제품 필드 + 트렌드 전용 필드 */
public class TrendItem extends Product {
    public String category;

    @SerializedName("rank_score")
    public int rankScore;      // 1위 = 100 기준 점수

    public String reason;      // AI가 쓴 트렌드 이유 한 문장 (AI 실패 시 빈 값)
    public String tag;         // 두 단어 태그 (예: "여름 필수")
}
