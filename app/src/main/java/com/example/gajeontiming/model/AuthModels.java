package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

/** 회원가입 · 로그인 API에 보내는 값과 받는 값 */
public final class AuthModels {

    private AuthModels() { }

    /** POST /api/auth/send-code */
    public static class EmailRequest {
        public final String email;
        public EmailRequest(String email) { this.email = email; }
    }

    /** POST /api/auth/verify-code */
    public static class VerifyRequest {
        public final String email;
        public final String code;
        public VerifyRequest(String email, String code) { this.email = email; this.code = code; }
    }

    /** POST /api/auth/signup/b2c */
    public static class SignupRequest {
        public final String email;
        public final String password;
        public final String nickname;
        /** 이용약관 · 개인정보 수집 · 이용 동의 (필수). false면 서버가 400으로 거절한다 */
        @SerializedName("agree_terms") public final boolean agreeTerms;
        public SignupRequest(String email, String password, String nickname, boolean agreeTerms) {
            this.email = email; this.password = password; this.nickname = nickname;
            this.agreeTerms = agreeTerms;
        }
    }

    /** POST /api/auth/login */
    public static class LoginRequest {
        public final String email;
        public final String password;
        public LoginRequest(String email, String password) { this.email = email; this.password = password; }
    }

    /** 가입 · 로그인 성공 응답 */
    public static class AuthResponse {
        public String token;
        @SerializedName("user_type") public String userType;   // b2c | b2b
        public String nickname;                                // 일반 회원
        @SerializedName("company_name") public String companyName;   // 사업자 회원
    }

    /** {"message": "..."} 형태의 단순 응답 */
    public static class MessageResponse {
        public String message;
    }
}
