package com.example.gajeontiming.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/** AI 상담 API(POST /api/insights/analyze)에 보내는 값과 받는 값 */
public final class InsightModels {

    private InsightModels() { }

    /** 직전 대화 한 줄 (서버가 "그럼 소음은?" 같은 이어지는 질문을 이해하는 데 씀) */
    public static class HistoryItem {
        public final String role;      // user | ai
        public final String content;
        public HistoryItem(String role, String content) { this.role = role; this.content = content; }
    }

    public static class Request {
        public final String query;
        public final String target = "b2c";          // 소비자 구매 상담 (b2b는 사업자 시장 분석)
        @SerializedName("top_k") public final int topK = 8;
        public final JsonObject context;             // 직전 답변의 conditions (없으면 null → 보내지 않음)
        public final List<HistoryItem> history;

        public Request(String query, JsonObject context, List<HistoryItem> history) {
            this.query = query;
            this.context = context;
            this.history = history;
        }
    }

    public static class Source {
        public int rank;
        public String text;
    }

    public static class Response {
        public String report;                        // 답변 (마크다운)
        public List<Source> sources;                 // 참고한 후기 · 상품 데이터
        public JsonObject conditions;                // 이번 질문에서 뽑은 조건 → 다음 질문 때 context로 보냄
        @SerializedName("answered_by") public String answeredBy;
    }
}
