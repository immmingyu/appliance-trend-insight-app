package com.example.gajeontiming.model;

import java.util.List;

/** GET /api/trend 응답: {"items": [...], "error": "..."} */
public class TrendResponse {
    public List<TrendItem> items;
    public String error;
}
