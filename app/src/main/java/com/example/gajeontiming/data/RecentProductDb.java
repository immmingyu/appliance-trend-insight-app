package com.example.gajeontiming.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.gajeontiming.model.Product;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * F-08 최근 본 제품을 기기 안의 SQLite에 저장한다 (서버에 보내지 않음, 로그인 없이 동작).
 * 같은 제품을 다시 보면 맨 앞으로 올라오고, 최대 MAX_ITEMS개까지만 남긴다.
 *
 * 테이블 recent_product
 *   title      제품명 (같은 제품 판별 기준, 기본키)
 *   json       Product 객체 전체를 JSON으로 (이미지 · 가격 · 링크 등)
 *   category   품목 이름 (구매 타이밍 · 비교 정확도용, 없으면 null)
 *   viewed_at  마지막으로 본 시각 (밀리초)
 */
public class RecentProductDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "gajeon.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "recent_product";
    private static final int MAX_ITEMS = 20;

    private static RecentProductDb instance;
    private final Gson gson = new Gson();

    /** 최근 본 제품 한 건: 제품 + 그때 알던 품목 */
    public static class Entry {
        public final Product product;
        public final String category;

        Entry(Product product, String category) {
            this.product = product;
            this.category = category;
        }
    }

    public static synchronized RecentProductDb get(Context context) {
        if (instance == null) instance = new RecentProductDb(context.getApplicationContext());
        return instance;
    }

    private RecentProductDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "title TEXT PRIMARY KEY, "
                + "json TEXT NOT NULL, "
                + "category TEXT, "
                + "viewed_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 버전을 올릴 일이 생기면 여기서 테이블을 바꾼다
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /** 제품을 봤다고 기록 (이미 있으면 시각만 갱신되어 맨 앞으로) */
    public void add(Product p, String category) {
        if (p == null || p.title == null) return;
        // TrendItem처럼 Product를 상속한 객체여도 공통 필드만 저장되게 Product로 옮겨 담는다
        Product plain = gson.fromJson(gson.toJson(p), Product.class);

        ContentValues v = new ContentValues();
        v.put("title", p.title);
        v.put("json", gson.toJson(plain));
        v.put("category", category);
        v.put("viewed_at", System.currentTimeMillis());

        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        // 오래된 것부터 지워서 MAX_ITEMS개만 남긴다
        db.execSQL("DELETE FROM " + TABLE + " WHERE title NOT IN ("
                + "SELECT title FROM " + TABLE + " ORDER BY viewed_at DESC LIMIT " + MAX_ITEMS + ")");
    }

    /** 최근에 본 순서대로 */
    public List<Entry> list(int limit) {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, new String[]{"json", "category"},
                null, null, null, null, "viewed_at DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                Product p = gson.fromJson(c.getString(0), Product.class);
                if (p != null) result.add(new Entry(p, c.isNull(1) ? null : c.getString(1)));
            }
        }
        return result;
    }

    /** 저장된 최근 본 제품 수 (☰ 메뉴 요약 칸) */
    public int count() {
        return (int) android.database.DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE);
    }

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }
}
