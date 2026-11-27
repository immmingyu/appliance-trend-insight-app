package com.example.gajeontiming.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StaleNoticeTest {

    @Test
    public void 정상_응답이면_안내_없음() {
        assertNull(StaleNotice.message(null, null));
        assertNull(StaleNotice.message(false, "2026-11-26T21:00+09:00"));
    }

    @Test
    public void 저장_시각을_한국_시간으로() {
        assertEquals("다나와 점검으로 실시간 가격을 가져올 수 없어 11월 26일 21:00 기준 저장된 정보를 보여 드려요",
                StaleNotice.message(true, "2026-11-26T21:00+09:00"));
        assertEquals("11월 27일 06:00", StaleNotice.format("2026-11-26T21:00:00+00:00"));
        assertEquals("11월 3일 09:05", StaleNotice.format("2026-11-03T09:05+09:00"));
    }

    @Test
    public void 시각을_못_읽으면_시각_없이() {
        assertEquals("다나와 점검으로 실시간 가격을 가져올 수 없어 저장된 정보를 보여 드려요",
                StaleNotice.message(true, null));
        assertEquals("다나와 점검으로 실시간 가격을 가져올 수 없어 저장된 정보를 보여 드려요",
                StaleNotice.message(true, "어제"));
    }
}
