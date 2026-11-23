package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/** 가격 알림 API(/api/user/alerts)에 보내는 값과 받는 값 */
public final class AlertModels {

    private AlertModels() { }

    /** 등록된 알림 하나 (서버 price_alert 테이블의 한 줄) */
    public static class Alert implements Serializable {
        @SerializedName("alert_id") public long alertId;
        @SerializedName("product_name") public String productName;
        @SerializedName("target_price") public double targetPrice;     // 이 가격 이하가 되면 알림
        @SerializedName("current_price") public double currentPrice;   // 등록할 때의 가격
        @SerializedName("product_url") public String productUrl;
        @SerializedName("created_at") public String createdAt;
        /** 서버가 매일 확인한 지금 최저가 (목표가 푸시도 이 값으로 판단). 아직 확인 전이면 null */
        @SerializedName("min_price") public Double minPrice;
        /** 서버가 마지막으로 가격을 확인한 시각. min_price와 함께 null이면 아직 한 번도 확인 전 */
        @SerializedName("last_updated") public String lastUpdated;
    }

    /** GET /api/user/alerts */
    public static class AlertListResponse {
        public List<Alert> alerts;
    }

    /** POST /api/user/alerts */
    public static class CreateRequest {
        @SerializedName("product_name") public final String productName;
        @SerializedName("target_price") public final long targetPrice;
        @SerializedName("current_price") public final long currentPrice;
        @SerializedName("product_url") public final String productUrl;
        @SerializedName("alert_type") public final String alertType = "below";

        public CreateRequest(String productName, long targetPrice, long currentPrice, String productUrl) {
            this.productName = productName;
            this.targetPrice = targetPrice;
            this.currentPrice = currentPrice;
            this.productUrl = productUrl == null ? "" : productUrl;
        }
    }

    public static class CreateResponse {
        @SerializedName("alert_id") public long alertId;
        public String message;
    }
}
