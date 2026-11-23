package com.example.gajeontiming.model;

import java.io.Serializable;

/**
 * 제품 하나. 검색 결과와 트렌드 목록이 공통으로 쓰는 필드.
 * Serializable: 인텐트로 다음 화면에 통째로 넘기기 위함.
 */
public class Product implements Serializable {
    public String id;
    public String title;
    public String brand;
    public int price;
    public String image;
    public String link;
    public String mallName;
    public double reviewScore;
    public int reviewCount;

    /** 같은 제품인지 (상품 번호 → 판매 링크 → 제품명 순으로 비교). 비교 목록에서 첫 제품을 빼는 데 쓴다 */
    public static boolean isSame(Product a, Product b) {
        if (a == null || b == null) return false;
        if (notEmpty(a.id) && a.id.equals(b.id)) return true;
        if (notEmpty(a.link) && a.link.equals(b.link)) return true;
        return notEmpty(a.title) && b.title != null && a.title.trim().equals(b.title.trim());
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
