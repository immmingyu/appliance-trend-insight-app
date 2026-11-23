package com.example.gajeontiming.b2b;

import com.example.gajeontiming.model.MarketModels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 시장 분석 제품 목록 정렬 (Android 의존 없음 → MarketSortTest). 웹과 같은 규칙.
 *   서버가 준 결과(최대 30개) 안에서만 정렬하고, 그 값이 없는 상품은 맨 뒤 (같으면 원래 순서 유지).
 *   RECOMMENDED(화면 "인기순")는 서버 순서 그대로 = 다나와 기본 정렬인 인기상품순
 *     (다나와 설명: 조회수 · 판매량 · 판매금액 · 연관 콘텐츠 수 점수). 네이버 검색으로 돌아가면 네이버 정확도순.
 *   POPULAR(화면 "리뷰 많은 순")는 리뷰 수(review_count), LATEST는 다나와 출시(제조)년월(release).
 *   네이버 date 정렬(쇼핑몰 등록일)은 신제품 순이 아니라서 쓰지 않는다.
 */
public final class MarketSort {

    public enum Order { RECOMMENDED, POPULAR, LATEST, PRICE_LOW, PRICE_HIGH, ENERGY }

    private MarketSort() { }

    /** 정렬한 새 목록 (원본은 그대로) */
    public static List<MarketModels.SearchItem> sort(List<MarketModels.SearchItem> items, Order order) {
        List<MarketModels.SearchItem> out = new ArrayList<>(items == null ? Collections.emptyList() : items);
        if (order == Order.RECOMMENDED) return out;
        Collections.sort(out, nullsLast(order));   // Collections.sort는 안정 정렬
        return out;
    }

    /** 결과에 그 정렬에 쓸 값이 하나라도 있는지 (없으면 칩을 숨긴다) */
    public static boolean available(List<MarketModels.SearchItem> items, Order order) {
        if (order == Order.RECOMMENDED) return true;
        if (items == null) return false;
        for (MarketModels.SearchItem it : items) {
            if (key(it, order) != null) return true;
        }
        return false;
    }

    private static Comparator<MarketModels.SearchItem> nullsLast(Order order) {
        boolean descending = order == Order.POPULAR || order == Order.LATEST || order == Order.PRICE_HIGH;
        return (a, b) -> {
            Double ka = key(a, order), kb = key(b, order);
            if (ka == null && kb == null) return 0;
            if (ka == null) return 1;
            if (kb == null) return -1;
            return descending ? Double.compare(kb, ka) : Double.compare(ka, kb);
        };
    }

    /** 정렬 기준 값. 없으면 null */
    static Double key(MarketModels.SearchItem it, Order order) {
        switch (order) {
            case POPULAR: return number(it.reviewCount);
            case LATEST: return releaseKey(it.release);
            case PRICE_LOW:
            case PRICE_HIGH: return it.price != null && it.price > 0 ? it.price : null;
            case ENERGY: return number(it.energyGrade);   // 1등급이 가장 좋음 (오름차순)
            default: return null;
        }
    }

    /** "2025-06" · "2025.06" · "202506" → 202506. 연도만 있으면 "2025" → 202500 */
    static Double releaseKey(String release) {
        if (release == null) return null;
        String digits = release.replaceAll("\\D", "");
        if (digits.length() >= 6) return Double.valueOf(digits.substring(0, 6));
        if (digits.length() == 4) return Double.valueOf(digits + "00");
        return null;
    }

    /** "1,234" · "1234" · "4.5" → 숫자. 빈 값 · 숫자 아님 → null */
    public static Double number(String value) {
        if (value == null) return null;
        String cleaned = value.replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
