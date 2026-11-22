package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GET /api/auth/me 응답: 회원 정보는 이 API 하나로 받는다 (예전 /api/user/me 대신).
 * 탈퇴 · 비활성화된 계정이면 서버가 401을 준다 → 로그아웃 (앱 시작 때 낡은 로그인 상태 정리).
 * 개인 회원도 tier "free" · tier_expires_at null로 온다.
 */
public class AuthMe {
    @SerializedName("user_id") public long userId;
    public String email;
    @SerializedName("user_type") public String userType;   // b2c | b2b
    public String role;                                    // user | admin
    public String status;                                  // active | pending | rejected
    @SerializedName("created_at") public String createdAt; // "2026-11-14 10:20:30.123+00:00"
    @SerializedName("social_providers") public List<String> socialProviders;   // 소셜 연결 ["kakao"] 등
    @SerializedName("has_password") public Boolean hasPassword;   // false면 소셜로만 로그인 (안 오면 null)
    public String tier;                                    // free | silver | gold | platinum
    @SerializedName("tier_expires_at") public String tierExpiresAt;
    public String nickname;                                // 개인 회원
    @SerializedName("company_name") public String companyName;   // 사업자 회원

    /** 화면에 보여 줄 이름 (닉네임, 없으면 상호명) */
    public String displayName() {
        if (nickname != null && !nickname.isEmpty()) return nickname;
        return companyName;
    }

    public boolean isB2b() {
        return "b2b".equals(userType);
    }
}
