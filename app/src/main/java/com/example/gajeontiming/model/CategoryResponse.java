package com.example.gajeontiming.model;

/** GET /api/category?name=상품명 → 품목 (못 찾으면 category가 null) */
public class CategoryResponse {
    public String name;
    public String category;
}
