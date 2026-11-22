package com.example.gajeontiming.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 로그인 상태를 기기에 저장한다 (SharedPreferences).
 * 앱을 껐다 켜도 로그인이 유지되고, 서버 토큰은 7일 뒤 만료된다.
 */
public final class SessionManager {

    // 이름을 바꾸면 res/xml/backup_rules.xml · data_extraction_rules.xml의 "session.xml"도 같이 바꿀 것
    // (로그인 토큰이 백업으로 다른 기기에 복원되지 않게 빼 두었다)
    private static final String PREF = "session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";
    private static final String KEY_PROVIDER = "provider";   // 소셜 로그인이면 "kakao" 등, 이메일 로그인이면 없음
    // 회원 정보 (로그인 응답 · /api/auth/me에서 갱신)
    private static final String KEY_USER_TYPE = "user_type";         // b2c | b2b
    private static final String KEY_STATUS = "status";               // active | pending | rejected
    private static final String KEY_TIER = "tier";                   // free | silver | gold | platinum
    private static final String KEY_TIER_EXPIRES = "tier_expires_at";
    // 로그인 방법 (/api/auth/me의 has_password, 모르면 없음)
    private static final String KEY_HAS_PASSWORD = "has_password";   // "true" | "false"

    private static SharedPreferences prefs;

    private SessionManager() { }

    /** 앱 시작 시 한 번 호출 (GajeonApp.onCreate) */
    public static void init(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(String token, String email, String name) {
        save(token, email, name, null);
    }

    /** provider: 소셜 로그인이면 "kakao" 등 (탈퇴 때 비밀번호 대신 "탈퇴" 입력으로 확인하는 데 쓴다) */
    public static void save(String token, String email, String name, String provider) {
        prefs.edit()
                .clear()   // 앞 계정의 회원 유형 · 등급이 남지 않게
                .putString(KEY_TOKEN, token)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, name)
                .putString(KEY_PROVIDER, provider)
                .apply();
    }

    /** 서버에서 받은 최신 이메일 · 이름으로 바꾼다 (토큰 · 로그인 방식은 그대로) */
    public static void updateProfile(String email, String name) {
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_NAME, name).apply();
    }

    /** 회원 유형 · 상태 · 구독 등급을 저장한다. null로 온 값은 지운다 (b2c는 tier가 없을 수 있음) */
    public static void saveAccount(String userType, String status, String tier, String tierExpiresAt) {
        prefs.edit()
                .putString(KEY_USER_TYPE, userType)
                .putString(KEY_STATUS, status)
                .putString(KEY_TIER, tier)
                .putString(KEY_TIER_EXPIRES, tierExpiresAt)
                .apply();
    }

    /** 비밀번호로 로그인할 수 있는 계정인지 저장 (서버 has_password) */
    public static void saveHasPassword(boolean hasPassword) {
        prefs.edit().putString(KEY_HAS_PASSWORD, String.valueOf(hasPassword)).apply();
    }

    /** "b2c" | "b2b". 아직 모르면 null */
    public static String getUserType() {
        return prefs == null ? null : prefs.getString(KEY_USER_TYPE, null);
    }

    /** 사업자 회원 (비즈니스 메뉴를 보여 줄지) */
    public static boolean isB2b() {
        return isLoggedIn() && "b2b".equals(getUserType());
    }

    public static String getStatus() {
        return prefs == null ? null : prefs.getString(KEY_STATUS, null);
    }

    /** free | silver | gold | platinum. 모르면 null */
    public static String getTier() {
        return prefs == null ? null : prefs.getString(KEY_TIER, null);
    }

    /** 구독 만료 시각 (ISO 문자열). 없으면 null */
    public static String getTierExpiresAt() {
        return prefs == null ? null : prefs.getString(KEY_TIER_EXPIRES, null);
    }

    /**
     * 비밀번호 없이 소셜로만 로그인하는 계정인지.
     * 서버가 알려 준 has_password를 먼저 보고, 아직 모르면 이 기기에서 소셜로 로그인했는지로 판단한다.
     * (비밀번호 변경 버튼 · 탈퇴 확인 방식에 쓴다)
     */
    public static boolean isSocialOnly() {
        String hasPassword = prefs == null ? null : prefs.getString(KEY_HAS_PASSWORD, null);
        if (hasPassword != null) return !Boolean.parseBoolean(hasPassword);
        return getProvider() != null;
    }

    /** 소셜 로그인 방식 ("kakao" 등). 이메일 로그인이면 null */
    public static String getProvider() {
        return prefs == null ? null : prefs.getString(KEY_PROVIDER, null);
    }

    public static void logout() {
        prefs.edit().clear().apply();
    }

    public static boolean isLoggedIn() {
        return getToken() != null;
    }

    public static String getToken() {
        return prefs == null ? null : prefs.getString(KEY_TOKEN, null);
    }

    public static String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    /** 화면에 보여줄 이름 (닉네임, 없으면 이메일) */
    public static String getName() {
        String name = prefs.getString(KEY_NAME, "");
        return (name == null || name.isEmpty()) ? getEmail() : name;
    }
}
