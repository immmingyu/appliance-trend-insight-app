package com.example.gajeontiming.model;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사업자 구독 등급 표시 (B2B 단계 5).
 * 등급 이름은 웹 frontend/src/config.js의 TIER_LABEL과 같게 유지할 것.
 * Android에 의존하지 않아 단위 테스트한다 (TiersTest).
 */
public final class Tiers {

    private Tiers() { }

    /** free → 무료, silver → 실버, gold → 골드, platinum → 플래티넘. 모르는 값은 그대로 */
    public static String label(String tier) {
        if (tier == null || tier.isEmpty() || "free".equals(tier)) return "무료";
        switch (tier) {
            case "silver": return "실버";
            case "gold": return "골드";
            case "platinum": return "플래티넘";
            default: return tier;
        }
    }

    /**
     * 마이페이지 한 줄: "골드 등급 · 2026.12.31까지" / 무료이거나 만료일이 없으면 "무료 등급".
     * 만료일은 웹처럼 무료가 아닐 때만 붙인다.
     */
    public static String summary(String tier, String expiresAt, TimeZone zone) {
        String text = label(tier) + " 등급";
        if (tier == null || "free".equals(tier)) return text;
        String date = formatDate(expiresAt, zone);
        return date == null ? text : text + " · " + date + "까지";
    }

    // 서버 isoformat: "2026-12-31T14:59:59.123456+00:00" · "2026-12-31 14:59:59" · "2026-12-31T14:59:59Z" · "2026-12-31"
    private static final Pattern ISO = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?)?\\s*(Z|[+-]\\d{2}:?\\d{2})?$");

    /** ISO 날짜 · 시각을 zone 기준 "yyyy.MM.dd"로. 읽을 수 없으면 null (시간대가 없으면 UTC로 본다) */
    public static String formatDate(String iso, TimeZone zone) {
        if (iso == null) return null;
        Matcher m = ISO.matcher(iso.trim());
        if (!m.matches()) return null;

        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        utc.clear();
        utc.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                m.group(4) == null ? 0 : Integer.parseInt(m.group(4)),
                m.group(5) == null ? 0 : Integer.parseInt(m.group(5)),
                m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
        long millis = utc.getTimeInMillis() - offsetMillis(m.group(7));
        // 날짜만 온 경우("2026-12-31")는 시간대 변환 없이 그 날짜 그대로
        if (m.group(4) == null) zone = TimeZone.getTimeZone("UTC");

        Calendar local = Calendar.getInstance(zone, Locale.ROOT);
        local.setTimeInMillis(millis);
        return String.format(Locale.ROOT, "%04d.%02d.%02d",
                local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1, local.get(Calendar.DAY_OF_MONTH));
    }

    private static long offsetMillis(String offset) {
        if (offset == null || "Z".equals(offset)) return 0;
        String digits = offset.substring(1).replace(":", "");
        int hours = Integer.parseInt(digits.substring(0, 2));
        int minutes = Integer.parseInt(digits.substring(2, 4));
        long millis = (hours * 60L + minutes) * 60_000L;
        return offset.charAt(0) == '-' ? -millis : millis;
    }
}
