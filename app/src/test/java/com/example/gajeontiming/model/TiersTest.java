package com.example.gajeontiming.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.TimeZone;

public class TiersTest {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    @Test
    public void 등급_이름은_웹_TIER_LABEL과_같다() {
        assertEquals("무료", Tiers.label("free"));
        assertEquals("실버", Tiers.label("silver"));
        assertEquals("골드", Tiers.label("gold"));
        assertEquals("플래티넘", Tiers.label("platinum"));
        assertEquals("무료", Tiers.label(null));
        assertEquals("diamond", Tiers.label("diamond"));   // 모르는 값은 그대로
    }

    @Test
    public void 무료는_만료일을_붙이지_않는다() {
        assertEquals("무료 등급", Tiers.summary("free", "2026-12-31T00:00:00+00:00", SEOUL));
        assertEquals("무료 등급", Tiers.summary(null, null, SEOUL));
    }

    @Test
    public void 유료는_만료일을_한국_날짜로_붙인다() {
        assertEquals("골드 등급 · 2026.12.31까지", Tiers.summary("gold", "2026-12-31T05:00:00+00:00", SEOUL));
        // UTC 12/31 15:00 = 한국 1/1 00:00
        assertEquals("실버 등급 · 2027.01.01까지", Tiers.summary("silver", "2026-12-31T15:00:00+00:00", SEOUL));
    }

    @Test
    public void 유료인데_만료일이_없거나_이상하면_등급만() {
        assertEquals("플래티넘 등급", Tiers.summary("platinum", null, SEOUL));
        assertEquals("플래티넘 등급", Tiers.summary("platinum", "언젠가", SEOUL));
    }

    @Test
    public void 서버_날짜_형식_여러_가지를_읽는다() {
        assertEquals("2026.12.31", Tiers.formatDate("2026-12-31T05:00:00.123456+00:00", SEOUL));
        assertEquals("2026.12.31", Tiers.formatDate("2026-12-31T05:00:00Z", SEOUL));
        assertEquals("2026.12.31", Tiers.formatDate("2026-12-31T23:30:00+09:00", SEOUL));
        assertEquals("2026.12.31", Tiers.formatDate("2026-12-31 05:00:00", SEOUL));   // 시간대 없음 → UTC
        assertEquals("2026.12.31", Tiers.formatDate("2026-12-31", SEOUL));            // 날짜만
        assertNull(Tiers.formatDate("", SEOUL));
        assertNull(Tiers.formatDate(null, SEOUL));
    }
}
