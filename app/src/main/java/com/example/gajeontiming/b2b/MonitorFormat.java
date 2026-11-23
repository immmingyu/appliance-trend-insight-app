package com.example.gajeontiming.b2b;

import java.util.Locale;

/**
 * 가격 모니터 표기 규칙 (Android 의존 없음 → MonitorFormatTest).
 *   변동률: -3.2 → "▼3.2%", 2.1 → "▲2.1%", 0 → "0.0%", 모름(null) → "-"
 *   색: 하락 파랑 · 상승 빨강 · 그 밖(보합 · 데이터 부족 · 실시간 조회)은 회색. 매입 판단은 하지 않는다
 */
public final class MonitorFormat {

    public enum Tone { DOWN, UP, NEUTRAL }

    private MonitorFormat() { }

    public static String pct(Double value) {
        if (value == null) return "-";
        double v = Math.round(value * 10) / 10.0;
        if (v == 0) return "0.0%";
        return String.format(Locale.ROOT, "%s%.1f%%", v < 0 ? "▼" : "▲", Math.abs(v));
    }

    /** 서버 signal_type(7일 평균가 ±2% 기준) → 색 */
    public static Tone tone(String signalType) {
        if ("down".equals(signalType)) return Tone.DOWN;
        if ("up".equals(signalType)) return Tone.UP;
        return Tone.NEUTRAL;   // neutral · realtime · 모르는 값
    }

    /** 변동률 숫자 색: 내리면 파랑, 오르면 빨강, 그 밖은 회색 */
    public static Tone changeTone(Double value) {
        if (value == null || Math.round(value * 10) == 0) return Tone.NEUTRAL;
        return value < 0 ? Tone.DOWN : Tone.UP;
    }
}
