package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

/**
 * GET /api/user/summary 응답: ☰ 메뉴 위쪽 요약 (로그인 필요, DB만 읽는 가벼운 API)
 * 예: {"display_name": "준영", "user_type": "b2c", "tier": "free", "tier_label": "무료",
 *      "alerts": {"count": 2, "limit": 3}, "alerts_reached": 1, "market_alerts_today": null}
 */
public class UserSummary {
    @SerializedName("display_name") public String displayName;   // 닉네임 또는 회사 이름
    @SerializedName("user_type") public String userType;         // b2c | b2b
    public String tier;                                          // free | silver | gold | platinum
    @SerializedName("tier_label") public String tierLabel;       // 무료 · 실버 …
    public Alerts alerts;
    @SerializedName("alerts_reached") public int alertsReached;  // 최근 목표가에 닿은 알림 수
    /** 사업자 오늘 시장 알림 수. 아직 기능이 없어 항상 null → null이 아니면 가운데 칸을 이걸로 바꾼다 */
    @SerializedName("market_alerts_today") public Integer marketAlertsToday;

    public static class Alerts {
        public int count;   // 등록한 알림 수
        public int limit;   // 등급별 한도
    }
}
