package com.example.gajeontiming.b2b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.gajeontiming.model.MarketModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarketSortTest {

    private static MarketModels.SearchItem item(String title, Double price, String release,
                                                String reviews, String grade) {
        MarketModels.SearchItem it = new MarketModels.SearchItem();
        it.title = title;
        it.price = price;
        it.release = release;
        it.reviewCount = reviews;
        it.energyGrade = grade;
        return it;
    }

    private static final List<MarketModels.SearchItem> ITEMS = Arrays.asList(
            item("A", 1_500_000.0, "2024-03", "120", "2"),
            item("B", 900_000.0, null, null, null),
            item("C", 2_100_000.0, "2025-06", "1,450", "1"),
            item("D", 1_200_000.0, "2025-01", "30", "3"));

    private static String titles(List<MarketModels.SearchItem> items) {
        StringBuilder sb = new StringBuilder();
        for (MarketModels.SearchItem it : items) sb.append(it.title);
        return sb.toString();
    }

    @Test
    public void 추천순은_서버_순서_그대로() {
        assertEquals("ABCD", titles(MarketSort.sort(ITEMS, MarketSort.Order.RECOMMENDED)));
    }

    @Test
    public void 인기순은_리뷰_많은_순_값없으면_맨뒤() {
        assertEquals("CADB", titles(MarketSort.sort(ITEMS, MarketSort.Order.POPULAR)));
    }

    @Test
    public void 최신순은_출시년월_내림차순() {
        assertEquals("CDAB", titles(MarketSort.sort(ITEMS, MarketSort.Order.LATEST)));
    }

    @Test
    public void 가격순() {
        assertEquals("BDAC", titles(MarketSort.sort(ITEMS, MarketSort.Order.PRICE_LOW)));
        assertEquals("CADB", titles(MarketSort.sort(ITEMS, MarketSort.Order.PRICE_HIGH)));
    }

    @Test
    public void 에너지_효율은_1등급부터() {
        assertEquals("CADB", titles(MarketSort.sort(ITEMS, MarketSort.Order.ENERGY)));
    }

    @Test
    public void 같은_값이면_원래_순서_유지() {
        List<MarketModels.SearchItem> same = Arrays.asList(
                item("X", 1.0, "2025-01", null, null), item("Y", 1.0, "2025-01", null, null));
        assertEquals("XY", titles(MarketSort.sort(same, MarketSort.Order.LATEST)));
    }

    @Test
    public void 값이_하나도_없으면_칩을_숨긴다() {
        List<MarketModels.SearchItem> naver = new ArrayList<>();
        naver.add(item("N", 1000.0, null, null, null));
        assertFalse(MarketSort.available(naver, MarketSort.Order.LATEST));
        assertFalse(MarketSort.available(naver, MarketSort.Order.POPULAR));
        assertFalse(MarketSort.available(naver, MarketSort.Order.ENERGY));
        assertTrue(MarketSort.available(naver, MarketSort.Order.PRICE_LOW));
        assertTrue(MarketSort.available(naver, MarketSort.Order.RECOMMENDED));
    }

    @Test
    public void 출시년월_형식() {
        assertEquals(202506.0, MarketSort.releaseKey("2025-06"), 0);
        assertEquals(202506.0, MarketSort.releaseKey("2025.06"), 0);
        assertEquals(202500.0, MarketSort.releaseKey("2025"), 0);
        assertEquals(null, MarketSort.releaseKey("미상"));
    }
}
