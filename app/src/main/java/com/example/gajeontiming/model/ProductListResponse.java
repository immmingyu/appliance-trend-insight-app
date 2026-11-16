package com.example.gajeontiming.model;

import java.util.List;

/** GET /api/naver/products 응답: {"items": [...], "total": 0, "page": 1, "display": 15} */
public class ProductListResponse {
    public List<Product> items;
    public int total;
    public int page;
    public int display;
}
