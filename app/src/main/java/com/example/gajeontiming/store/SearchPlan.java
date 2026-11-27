package com.example.gajeontiming.store;

import com.example.gajeontiming.scan.ModelNameExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 결과가 0건일 때 차례로 넓혀 볼 검색 순서 (순수 Java, JUnit 테스트 가능).
 * 제품 목록 화면(ProductListActivity.buildAttempts)과 같은 규칙을 매장 모드에서도 쓰기 위해 분리했다.
 *
 *   [0] 원래 검색어 + 품목
 *   [1..] 모델명이면 뒷자리를 2글자씩 줄인 검색어 + 품목 (최소 6자, 최대 2번)
 *   [마지막] 품목을 알았다면 원래 검색어, 품목 없이 (서버 소모품 차단이 꺼지므로 맨 마지막)
 */
public final class SearchPlan {

    public static final class Attempt {
        public final String query;
        public final String category;   // null이면 품목 필터 없이

        Attempt(String query, String category) {
            this.query = query;
            this.category = category;
        }
    }

    private SearchPlan() { }

    public static List<Attempt> build(String query, String category) {
        List<Attempt> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return list;
        String q = query.trim();
        list.add(new Attempt(q, category));
        if (ModelNameExtractor.looksLikeModel(q)) {
            for (String shorter : ModelNameExtractor.shorterQueries(q)) {
                list.add(new Attempt(shorter, category));
            }
        }
        if (category != null) list.add(new Attempt(q, null));
        return list;
    }
}
