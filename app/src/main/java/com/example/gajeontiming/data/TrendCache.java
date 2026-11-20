package com.example.gajeontiming.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.gajeontiming.model.TrendResponse;
import com.google.gson.Gson;

/**
 * 마지막으로 받은 트렌드 TOP10을 기기에 저장한다 (SharedPreferences에 JSON 문자열로).
 * 서버가 잠들어 있어 응답이 늦어도, 앱을 켜자마자 지난번 목록을 먼저 보여 주기 위함.
 */
public final class TrendCache {

    private static final String PREF = "trend_cache";
    private static final String KEY_JSON = "json";

    private static final Gson GSON = new Gson();

    private TrendCache() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context context, TrendResponse response) {
        prefs(context).edit()
                .putString(KEY_JSON, GSON.toJson(response))
                .apply();
    }

    /** 저장된 트렌드. 없거나 읽을 수 없으면 null */
    public static TrendResponse load(Context context) {
        String json = prefs(context).getString(KEY_JSON, null);
        if (json == null) return null;
        try {
            TrendResponse cached = GSON.fromJson(json, TrendResponse.class);
            if (cached == null || cached.items == null || cached.items.isEmpty()) return null;
            return cached;
        } catch (Exception e) {
            return null;   // 앱 업데이트로 모델 구조가 바뀌어 못 읽는 경우 등
        }
    }
}
