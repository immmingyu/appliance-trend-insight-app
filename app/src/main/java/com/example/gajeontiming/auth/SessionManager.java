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
