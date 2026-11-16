package com.example.gajeontiming.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 로그인 상태를 기기에 저장한다 (SharedPreferences).
 * 앱을 껐다 켜도 로그인이 유지되고, 서버 토큰은 7일 뒤 만료된다.
 */
public final class SessionManager {

    private static final String PREF = "session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";

    private static SharedPreferences prefs;

    private SessionManager() { }

    /** 앱 시작 시 한 번 호출 (GajeonApp.onCreate) */
    public static void init(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(String token, String email, String name) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, name)
                .apply();
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
