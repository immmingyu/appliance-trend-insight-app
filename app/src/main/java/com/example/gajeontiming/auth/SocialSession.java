package com.example.gajeontiming.auth;

/**
 * 소셜 로그인으로 받은 우리 서비스 토큰을 저장하고, 이메일 · 닉네임 · 회원 유형을 서버에서 받아 채운다.
 * (/social/token · /social/complete 응답에는 토큰만 있어서 AccountSync로 한 번 더 묻는다)
 * 프로필을 못 받아도 로그인은 된 상태라 done은 항상 불린다.
 */
public final class SocialSession {

    private SocialSession() { }

    public static void start(String token, String provider, String fallbackName, Runnable done) {
        SessionManager.save(token, "", fallbackName == null ? "" : fallbackName, provider);
        SessionManager.saveAccount("b2c", "active", null, null);   // 소셜 가입은 개인 회원만 (서버 응답으로 다시 맞춘다)
        AccountSync.refresh(done);
    }
}
