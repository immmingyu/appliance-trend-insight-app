package com.example.gajeontiming.store;

import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.scan.AccessoryFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 매장 모드: 검색 결과 중 가격표의 모델명과 "가장 맞는 본품"을 고른다 (순수 Java, JUnit 테스트 가능).
 *
 *   1) 소모품 · 부속품(AccessoryFilter)은 후보에서 뺀다
 *   2) 제품명과 모델명을 띄어쓰기 · 하이픈 · 점을 지우고 대문자로 맞춘 뒤 비교한다
 *      - 모델명 전체가 들어 있으면 100점 (정확히 같은 모델)
 *      - 아니면 모델명 앞부분이 몇 글자까지 들어 있는지 × 5점 (4글자 이상일 때만, 최대 95점)
 *   3) 품목을 알면 제품명의 품목이 같을 때 +3점, 가격이 있으면 +1점
 *   4) 점수가 같으면 서버가 준 순서(정확도순)를 지킨다
 * 모델명이 아닌 검색어("LG 에어컨")면 모델 점수는 모두 0이라 서버 순서 그대로가 된다.
 */
public final class ProductMatcher {

    public static final int EXACT = 100;

    public static final class Match {
        public final Product product;
        public final int score;

        Match(Product product, int score) {
            this.product = product;
            this.score = score;
        }

        /** 가격표 모델명과 정확히 같은 제품인지 */
        public boolean isExact() {
            return score >= EXACT;
        }
    }

    private ProductMatcher() { }

    /** 점수 높은 순으로 최대 limit개 */
    public static List<Match> rank(List<Product> items, String model, String category, int limit) {
        List<Match> result = new ArrayList<>();
        if (items == null) return result;
        String m = normalize(model);

        for (Product p : items) {
            if (p == null || p.title == null || p.title.trim().isEmpty()) continue;
            if (AccessoryFilter.isAccessory(p.title, p.price)) continue;
            int score = modelScore(normalize(p.title), m);
            if (category != null && category.equals(Categories.detect(p.title))) score += 3;
            if (p.price > 0) score += 1;
            result.add(new Match(p, score));
        }
        // 안정 정렬이라 점수가 같으면 서버 순서가 유지된다
        result.sort((a, b) -> b.score - a.score);
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    static int modelScore(String title, String model) {
        if (model.length() < 4) return 0;
        if (title.contains(model)) return EXACT;
        for (int len = model.length() - 1; len >= 4; len--) {
            if (title.contains(model.substring(0, len))) return Math.min(len * 5, EXACT - 5);
        }
        return 0;
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_./]", "");
    }
}
