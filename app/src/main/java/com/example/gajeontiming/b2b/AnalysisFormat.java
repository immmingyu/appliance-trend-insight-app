package com.example.gajeontiming.b2b;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 제품 시장 분석 표기 규칙 (Android 의존 없음 → AnalysisFormatTest).
 */
public final class AnalysisFormat {

    private AnalysisFormat() { }

    /**
     * 서버가 품목을 알아보는 단어 (backend config._CATEGORY_MAP의 대표 단어).
     * 제품명에 품목 단어가 없어 분석이 안 되면 이 중 하나를 골라 hint로 다시 보낸다.
     */
    public static final String[] CATEGORY_HINTS = {
            "냉장고", "김치냉장고", "세탁기", "건조기", "에어컨", "공기청정기", "로봇청소기", "청소기",
            "식기세척기", "TV", "에어프라이어", "전기밥솥", "전자레인지", "커피머신", "가습기", "제습기",
            "선풍기", "히터", "헤어드라이어", "사운드바", "스피커",
    };

    /** 검색어나 제품명에 이미 품목 단어가 있으면 그 단어, 없으면 null */
    public static String detectCategoryHint(String... texts) {
        // 긴 단어부터 봐야 "김치냉장고"가 "냉장고"로, "로봇청소기"가 "청소기"로 잡히지 않는다
        String best = null;
        for (String text : texts) {
            if (text == null) continue;
            for (String kw : CATEGORY_HINTS) {
                if (text.contains(kw) && (best == null || kw.length() > best.length())) best = kw;
            }
        }
        return best;
    }

    /** 1234567.0 → "1,234,567원", null · 0 이하 → null */
    public static String won(Double value) {
        if (value == null || value <= 0) return null;
        return NumberFormat.getNumberInstance(Locale.KOREA).format(Math.round(value)) + "원";
    }

    /** 출시(등록)월: "2025-06" → "2025.06", 그 밖은 그대로, 비어 있으면 null */
    public static String month(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        if (v.matches("\\d{4}-\\d{2}")) return v.substring(0, 4) + "." + v.substring(5, 7);
        return v;
    }

    /** 그래프 축 날짜: "2026-11-22" → "11.22", "2025-03" → "25.03", 그 밖은 그대로 */
    public static String shortDate(String date) {
        if (date == null) return "";
        if (date.matches("\\d{4}-\\d{2}-\\d{2}.*")) return date.substring(5, 7) + "." + date.substring(8, 10);
        if (date.matches("\\d{4}-\\d{2}")) return date.substring(2, 4) + "." + date.substring(5, 7);
        if (date.matches("\\d{6}")) return date.substring(2, 4) + "." + date.substring(4, 6);   // "202503"
        return date;
    }

    /** 검색 결과 한 줄 부가 정보: "에너지 1등급 · 월 전기요금 약 12,300원 (하루 8시간 기준)" */
    public static String energy(String grade, Double monthlyCost, String basis) {
        StringBuilder sb = new StringBuilder();
        if (grade != null && !grade.trim().isEmpty()) sb.append("에너지 ").append(grade.trim()).append("등급");
        String cost = won(monthlyCost);
        if (cost != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("월 전기요금 약 ").append(cost);
            if (basis != null && !basis.isEmpty()) sb.append(" (").append(basis).append(')');
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
