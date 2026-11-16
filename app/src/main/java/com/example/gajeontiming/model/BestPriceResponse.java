package com.example.gajeontiming.model;

/** GET /api/best-price 응답: 지금 가장 싼 판매처 하나 (못 찾으면 available=false) */
public class BestPriceResponse {
    public boolean available;
    public long price;
    public String link;
    public String mallName;
    public String source;     // "다나와" | "네이버"
}
