package com.example.gajeontiming.alert;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.example.gajeontiming.AlertListActivity;
import com.example.gajeontiming.MainActivity;
import com.example.gajeontiming.ProductDetailActivity;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.Product;

import java.util.HashMap;
import java.util.Map;

/**
 * 가격 알림 푸시를 눌렀을 때 열 화면.
 * 서버 data: {type: "price_alert", alert_id, reason, product_name, product_url, image, price, category}
 *   제품 정보가 있으면 → 홈 위에 제품 상세 (뒤로 가면 홈)
 *   없으면(예전 서버) → 홈 위에 가격 알림 목록
 * 앱이 꺼져 있을 때: 안드로이드가 알림을 띄우고, 누르면 시작 화면(SplashActivity)이 data를 extras로 받는다.
 * 앱을 쓰는 중: AlertNotifier가 이 화면 목록으로 알림을 만든다.
 */
public final class PushRoute {

    private PushRoute() { }

    /** 시작 화면이 받은 extras에서 (가격 알림이 아니면 null) */
    public static Intent[] fromExtras(Context context, Bundle extras) {
        if (extras == null) return null;
        Map<String, String> data = new HashMap<>();
        for (String key : new String[]{"type", "alert_id", "reason", "product_name", "product_url",
                "image", "price", "category"}) {
            String v = extras.getString(key);
            if (v != null) data.put(key, v);
        }
        return fromData(context, data);
    }

    /** 푸시 data에서 (가격 알림이 아니면 null) */
    public static Intent[] fromData(Context context, Map<String, String> data) {
        if (data == null || !"price_alert".equals(data.get("type"))) return null;
        // 홈을 맨 아래에 둔다 (이미 열려 있으면 그 홈을 다시 쓰고 위 화면들은 닫는다)
        Intent home = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Product product = productFrom(data);
        if (product == null) return new Intent[]{home, new Intent(context, AlertListActivity.class)};

        String category = data.get("category");
        if (category == null || category.isEmpty()) category = Categories.detect(product.title);
        Intent detail = new Intent(context, ProductDetailActivity.class)
                .putExtra(ProductDetailActivity.EXTRA_PRODUCT, product)
                .putExtra(ProductDetailActivity.EXTRA_CATEGORY, category);
        return new Intent[]{home, detail};
    }

    /** 푸시 data → 제품 (제품명이 없으면 null). 가격은 알림을 보낸 시점의 최저가 */
    static Product productFrom(Map<String, String> data) {
        String name = data.get("product_name");
        if (name == null || name.trim().isEmpty()) return null;
        Product p = new Product();
        p.title = name.trim();
        p.link = emptyToNull(data.get("product_url"));
        p.image = emptyToNull(data.get("image"));
        try {
            p.price = (int) Math.min(Integer.MAX_VALUE, Long.parseLong(data.get("price")));
        } catch (NumberFormatException e) {
            p.price = 0;
        }
        return p;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
