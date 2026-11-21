package com.example.gajeontiming.auth;

/** 카카오 · Google SDK 로그인 결과. onToken에는 서버 /api/auth/social/token 에 보낼 토큰이 온다 */
public interface SocialLoginCallback {
    void onToken(String token);
    void onCancel();
    void onError(String message);
}
