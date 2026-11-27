package com.example.gajeontiming.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.gajeontiming.model.Product;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 매장 모드: 검색 결과에서 가격표 모델명과 가장 맞는 본품 고르기 단위 테스트 */
public class ProductMatcherTest {

    private static Product p(String title, int price) {
        Product x = new Product();
        x.title = title;
        x.price = price;
        return x;
    }

    @Test
    public void 모델명이_정확히_같은_본품을_1등으로_고른다() {
        List<Product> items = new ArrayList<>();
        items.add(p("LG 휘센 오브제컬렉션 스탠드 에어컨 FQ18D7DWBN", 2_390_000));   // 뒷자리 다른 모델
        items.add(p("LG 휘센 오브제컬렉션 FQ18D7DWAN", 2_490_000));               // 정확히 같은 모델
        List<ProductMatcher.Match> r = ProductMatcher.rank(items, "FQ18D7DWAN", "에어컨", 3);
        assertEquals("LG 휘센 오브제컬렉션 FQ18D7DWAN", r.get(0).product.title);
        assertTrue(r.get(0).isExact());
        assertFalse(r.get(1).isExact());
    }

    @Test
    public void 소모품은_후보에서_뺀다() {
        List<Product> items = new ArrayList<>();
        items.add(p("LG FQ19D7DWAN FQ18VCDRAN FQ18DBDNAN 스탠드 에어컨 극세 필터", 12_900));
        items.add(p("LG 휘센 FQ18D7DWAN 스탠드 에어컨", 2_490_000));
        List<ProductMatcher.Match> r = ProductMatcher.rank(items, "FQ18D7DWAN", null, 3);
        assertEquals(1, r.size());
        assertTrue(r.get(0).product.title.contains("휘센"));
    }

    @Test
    public void 하이픈_띄어쓰기가_달라도_같은_모델로_본다() {
        List<Product> items = new ArrayList<>();
        items.add(p("쿠쿠 IH 전기압력밥솥 CRP LHTR0610FW", 389_000));
        List<ProductMatcher.Match> r = ProductMatcher.rank(items, "CRP-LHTR0610FW", null, 3);
        assertTrue(r.get(0).isExact());
    }

    @Test
    public void 앞부분만_같으면_더_길게_겹치는_쪽이_앞선다() {
        List<Product> items = new ArrayList<>();
        items.add(p("LG 에어컨 FQ18A", 1_900_000));        // FQ18 까지 겹침
        items.add(p("LG 에어컨 FQ18D7DW", 2_300_000));     // FQ18D7DW 까지 겹침
        List<ProductMatcher.Match> r = ProductMatcher.rank(items, "FQ18D7DWAN", null, 3);
        assertEquals("LG 에어컨 FQ18D7DW", r.get(0).product.title);
    }

    @Test
    public void 모델명이_아닌_검색어면_서버_순서를_지킨다() {
        List<Product> items = new ArrayList<>();
        items.add(p("삼성 비스포크 AI 무풍 에어컨 AF17", 2_100_000));
        items.add(p("LG 휘센 스탠드 에어컨 FQ17", 1_900_000));
        List<ProductMatcher.Match> r = ProductMatcher.rank(items, "에어컨", null, 3);
        assertEquals("삼성 비스포크 AI 무풍 에어컨 AF17", r.get(0).product.title);
    }

    @Test
    public void 후보는_최대_개수까지만() {
        List<Product> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(p("LG 에어컨 FQ18D7 " + i, 2_000_000));
        assertEquals(3, ProductMatcher.rank(items, "FQ18D7DWAN", null, 3).size());
    }
}
