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

    /**
     * POST /api/auth/withdraw. 이메일 회원은 비밀번호, 소셜 회원은 confirm="탈퇴"로 본인 확인을 한다.
     * 쓰지 않는 쪽은 null (Gson은 null 필드를 보내지 않는다)
     */
    public static class WithdrawRequest {
        public final String password;
        public final String confirm;
        private WithdrawRequest(String password, String confirm) { this.password = password; this.confirm = confirm; }
        public static WithdrawRequest byPassword(String password) { return new WithdrawRequest(password, null); }
        public static WithdrawRequest byConfirm(String word) { return new WithdrawRequest(null, word); }
    }

    /** POST /api/auth/password/change: 이메일 회원 비밀번호 변경 (소셜 회원은 서버가 400) */
    public static class PasswordChangeRequest {
        @SerializedName("current_password") public final String currentPassword;
        @SerializedName("new_password") public final String newPassword;
        @SerializedName("new_password_confirm") public final String newPasswordConfirm;
        public PasswordChangeRequest(String currentPassword, String newPassword, String newPasswordConfirm) {
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
            this.newPasswordConfirm = newPasswordConfirm;
        }
    }

    /** POST /api/auth/social/token: 카카오 액세스 토큰 · 구글 ID 토큰을 우리 로그인 토큰으로 바꾼다 */
    public static class SocialTokenRequest {
        public final String provider;   // "kakao" | "google"
        public final String token;
        public SocialTokenRequest(String provider, String token) { this.provider = provider; this.token = token; }
    }

    /**
     * /api/auth/social/token 응답. 둘 중 하나가 온다.
     *   기존 회원: token · user_type → 바로 로그인
     *   처음 온 사람: signup(가입 티켓, 15분) · provider · name → 약관 동의 화면(SocialSignupActivity)
     */
    public static class SocialTokenResponse {
        public String token;
        @SerializedName("user_type") public String userType;
        public String signup;
        public String provider;
        public String name;
    }

    /** POST /api/auth/social/complete: 소셜 첫 가입 마무리 (약관 동의 필수). 응답은 AuthResponse(token · user_type) */
    public static class SocialCompleteRequest {
        public final String ticket;
        @SerializedName("agree_terms") public final boolean agreeTerms;
        public final String nickname;
        public SocialCompleteRequest(String ticket, boolean agreeTerms, String nickname) {
            this.ticket = ticket; this.agreeTerms = agreeTerms; this.nickname = nickname;
        }
    }

    /** {"message": "..."} 형태의 단순 응답 */
    public static class MessageResponse {
        public String message;
    }
}
