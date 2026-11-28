package com.example.gajeontiming.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.gajeontiming.R;

/**
 * ☰ 메뉴가 기억하는 값 (이 기기에만 저장):
 *   - 매장 모드에서 마지막으로 판단한 모델명 · 가전무쌍 점수 → 매장 모드 버튼 아래 "최근 찍은 제품" 줄
 *   - 마지막에 본 품목 → 메뉴 품목 칩 중 그것만 선택 상태
 */
public final class DrawerPrefs {

    private static final String PREF = "drawer";
    private static final String KEY_STORE_MODEL = "store_model";
    private static final String KEY_STORE_SCORE = "store_score";
    private static final String KEY_LAST_CATEGORY = "last_category";

    private DrawerPrefs() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 매장 모드에서 판단을 마쳤을 때 (점수는 TimingJudge 0~100) */
    public static void saveStoreResult(Context c, String model, int score) {
        if (model == null || model.trim().isEmpty()) return;
        prefs(c).edit().putString(KEY_STORE_MODEL, model.trim()).putInt(KEY_STORE_SCORE, score).apply();
    }

    /** 마지막으로 찍은 모델명. 없으면 null */
    public static String storeModel(Context c) {
        return prefs(c).getString(KEY_STORE_MODEL, null);
    }

    public static int storeScore(Context c) {
        return prefs(c).getInt(KEY_STORE_SCORE, 0);
    }

    public static void saveLastCategory(Context c, String category) {
        if (category == null || category.isEmpty()) return;
        prefs(c).edit().putString(KEY_LAST_CATEGORY, category).apply();
    }

    public static String lastCategory(Context c) {
        return prefs(c).getString(KEY_LAST_CATEGORY, null);
    }

    // ── 판단 4단계: TimingJudge.grade와 같은 기준 (75 · 55 · 40) ──

    /** 최고 · 좋음 · 보통 · 대기 */
    public static int verdictLabel(int score) {
        if (score >= 75) return R.string.verdict_best;
        if (score >= 55) return R.string.verdict_good;
        if (score >= 40) return R.string.verdict_hold;
        return R.string.verdict_wait;
    }

    /** verdict_* 색 */
    public static int verdictColor(int score) {
        if (score >= 75) return R.color.verdict_best;
        if (score >= 55) return R.color.verdict_good;
        if (score >= 40) return R.color.verdict_hold;
        return R.color.verdict_wait;
    }
}
