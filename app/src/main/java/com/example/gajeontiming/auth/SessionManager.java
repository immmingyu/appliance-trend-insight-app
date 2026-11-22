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
    // 사업자 승인 결과 안내 창을 아직 안 띄웠으면 "active" | "rejected" (pending에서 바뀐 걸 앱이 본 순간 저장)
    private static final String KEY_STATUS_NOTICE = "b2b_status_notice";
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
        SharedPreferences.Editor editor = prefs.edit();
        // 사업자 상태가 승인 · 거절로 바뀜 (대기 → 승인, 대기 → 거절, 거절 → 승인 등)
        // → 다음에 앱 화면이 뜰 때 안내 창 한 번 (AccountSync.onScreenResumed)
        String before = getStatus();
        if ("b2b".equals(userType) && before != null && !before.equals(status)
                && ("active".equals(status) || "rejected".equals(status))) {
            editor.putString(KEY_STATUS_NOTICE, status);
        }
        editor
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

    /**
     * 승인된 사업자 회원 (비즈니스 메뉴를 보여 줄지).
     * 승인 대기 · 거절 사업자는 개인 회원과 같은 기능만 쓴다 (서버 B2B API도 403).
     */
    public static boolean isB2b() {
        return isLoggedIn() && "b2b".equals(getUserType()) && "active".equals(getStatus());
    }

    /** 사업자로 가입 신청했고 관리자 승인을 기다리는 중 */
    public static boolean isB2bPending() {
        return isLoggedIn() && "b2b".equals(getUserType()) && "pending".equals(getStatus());
    }

    /** 사업자 승인이 거절됨 (개인 기능은 계속 사용) */
    public static boolean isB2bRejected() {
        return isLoggedIn() && "b2b".equals(getUserType()) && "rejected".equals(getStatus());
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

    /** 아직 안 띄운 사업자 승인 결과 ("active" | "rejected"). 돌려주면서 지운다 (한 번만). 없으면 null */
    public static String consumeStatusNotice() {
        String notice = prefs == null ? null : prefs.getString(KEY_STATUS_NOTICE, null);
        if (notice != null) prefs.edit().remove(KEY_STATUS_NOTICE).apply();
        return notice;
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
