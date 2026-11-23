package com.example.gajeontiming.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.gajeontiming.model.Product;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PushRouteTest {

    @Test
    public void 푸시_data로_제품을_만든다() {
        Map<String, String> data = new HashMap<>();
        data.put("type", "price_alert");
        data.put("product_name", " LG전자 스탠바이미2 Max 32LX6BPGA ");
        data.put("product_url", "https://prod.danawa.com/info/?pcode=1");
        data.put("image", "");
        data.put("price", "1278780");
        Product p = PushRoute.productFrom(data);
        assertEquals("LG전자 스탠바이미2 Max 32LX6BPGA", p.title);
        assertEquals("https://prod.danawa.com/info/?pcode=1", p.link);
        assertNull(p.image);                 // 빈 문자열은 없음으로
        assertEquals(1278780, p.price);
    }

    @Test
    public void 가격이_없거나_이상하면_0() {
        Map<String, String> data = new HashMap<>();
        data.put("product_name", "제품");
        assertEquals(0, PushRoute.productFrom(data).price);
        data.put("price", "abc");
        assertEquals(0, PushRoute.productFrom(data).price);
    }

    @Test
    public void 제품명이_없으면_null() {
        Map<String, String> data = new HashMap<>();
        data.put("type", "price_alert");
        data.put("alert_id", "3");
        assertNull(PushRoute.productFrom(data));
        data.put("product_name", "  ");
        assertNull(PushRoute.productFrom(data));
    }
}
