package com.example.gajeontiming.network;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 다나와 장애 때 서버가 저장해 둔 검색 결과로 응답하면 {"stale": true, "stale_at": "2026-11-26T21:00+09:00"}가 붙는다.
 * 목록 위에 띄울 안내 문구를 만든다 (Android 의존 없음 → StaleNoticeTest).
 */
public final class StaleNotice {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");
    // 서버는 분까지 보내지만(timespec="minutes") 초가 붙어 와도 읽는다
    private static final String[] PATTERNS = {"yyyy-MM-dd'T'HH:mmXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"};

    private StaleNotice() { }

    /** 저장된 정보가 아니면 null */
    public static String message(Boolean stale, String staleAt) {
        if (!Boolean.TRUE.equals(stale)) return null;
        String when = format(staleAt);
        return when != null
                ? "다나와 점검으로 실시간 가격을 가져올 수 없어 " + when + " 기준 저장된 정보를 보여 드려요"
                : "다나와 점검으로 실시간 가격을 가져올 수 없어 저장된 정보를 보여 드려요";
    }

    /** "2026-11-26T21:00+09:00" → "11월 26일 21:00" (한국 시간). 읽을 수 없으면 null */
    static String format(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        for (String pattern : PATTERNS) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(pattern, Locale.US);
                in.setLenient(false);
                Date date = in.parse(iso.trim());
                SimpleDateFormat out = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA);
                out.setTimeZone(SEOUL);
                return out.format(date);
            } catch (ParseException ignored) {
                // 다음 형식으로
            }
        }
        return null;
    }
}
