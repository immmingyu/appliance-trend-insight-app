package com.example.gajeontiming.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GET /api/naver/products 응답: {"items": [...], "total": 0, "page": 1, "display": 15}
 * 다나와 장애 때 서버가 저장된 결과로 응답하면 stale · stale_at이 붙는다 (정상이면 없음 → null).
 */
public class ProductListResponse {
    public List<Product> items;
    public int total;
    public int page;
    public int display;
    public Boolean stale;
    @SerializedName("stale_at") public String staleAt;   // 저장 시각 "2026-11-26T21:00+09:00"
}
