package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GET /api/ai-compare 응답 (로그인 필요).
 * products[0]이 첫 번째 제품(A), products[1]이 두 번째 제품(B)의 분석이다.
 */
public class AiCompareResponse {
    public List<ProductResult> products;
    public Integer winner;          // 0 = A, 1 = B, null = 무승부

    @SerializedName("winner_reason")
    public String winnerReason;     // 승자를 고른 이유 한 문장

    public String verdict;          // 어떤 사용자에게 어떤 제품이 맞는지 2문장
    public String error;            // 서버에서 AI 호출이 실패하면 들어 있음

    public static class ProductResult {
        public List<String> pros;
        public List<String> cons;
        public Double score;        // 1.0 ~ 5.0, 실패하면 null
        public String summary;
    }
}
