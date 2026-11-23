package com.example.gajeontiming.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProductTest {

    private static Product p(String id, String link, String title) {
        Product p = new Product();
        p.id = id;
        p.link = link;
        p.title = title;
        return p;
    }

    @Test
    public void 상품번호가_같으면_같은_제품() {
        assertTrue(Product.isSame(p("1", null, "A"), p("1", null, "B")));
    }

    @Test
    public void 링크가_같으면_같은_제품() {
        assertTrue(Product.isSame(p(null, "https://x/1", "A"), p("9", "https://x/1", "B")));
    }

    @Test
    public void 번호_링크가_없으면_제품명으로() {
        assertTrue(Product.isSame(p(null, null, "LG 뉴 스타일러 "), p(null, null, "LG 뉴 스타일러")));
        assertFalse(Product.isSame(p("1", "https://x/1", "A"), p("2", "https://x/2", "B")));
    }

    @Test
    public void null이면_다른_제품() {
        assertFalse(Product.isSame(null, p("1", null, "A")));
        assertFalse(Product.isSame(p(null, null, null), p(null, null, null)));
    }
}
