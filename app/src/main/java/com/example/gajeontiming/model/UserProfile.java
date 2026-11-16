package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

/** GET /api/user/me 응답 (로그인 필요) */
public class UserProfile {
    public String email;
    @SerializedName("user_type") public String userType;     // b2c | b2b
    public String nickname;                                  // 일반 회원
    @SerializedName("company_name") public String companyName; // 사업자 회원
    @SerializedName("created_at") public String createdAt;   // "2026-11-14 10:20:30.123+00:00"
    public String tier;                                      // free 등
}
