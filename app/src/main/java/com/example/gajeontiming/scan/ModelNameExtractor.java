package com.example.gajeontiming.scan;

import com.example.gajeontiming.model.Categories;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * F-16 사진에서 읽은 글자 중 "모델명처럼 생긴 것"을 골라낸다.
 * 안드로이드에 의존하지 않는 순수 Java라서 JUnit으로 바로 테스트할 수 있다.
 *
 * 모델명 판별 규칙
 *   1) 영문 대문자 · 숫자 · 하이픈(-) · 밑줄(_) · 슬래시(/) · 점(.)으로만 이루어짐
 *   2) 길이 5~20자
 *   3) 영문과 숫자가 둘 다 들어 있음 (예: AF17B6474TZS, RF85C9101AP, CRP-LHTR0610FW)
 *   4) 가격 · 용량 · 날짜처럼 보이는 것은 제외 (예: 1,290,000 / 220V / 2026.11.12 / 17.3KG)
 * 점수: 길이가 길수록, 숫자와 영문이 고르게 섞일수록, 하이픈이 있을수록 앞으로
 */
public final class ModelNameExtractor {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Z0-9\\-_/.]{5,20}$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    /** 단위가 붙은 숫자: 220V, 60HZ, 17KG, 1500W, 300L, 55INCH, 5KWH 등 */
    private static final Pattern UNIT_ONLY = Pattern.compile(
            "^[0-9.,/\\-]+(V|HZ|KG|G|W|KW|KWH|L|ML|MM|CM|M|INCH|IN|DB|RPM|EA|P|K)$");
    /** 날짜 · 시간 */
    private static final Pattern DATE_LIKE = Pattern.compile("^\\d{2,4}[.\\-/]\\d{1,2}([.\\-/]\\d{1,2})?$");

    private ModelNameExtractor() { }

    /** 결과: 모델명 후보(점수 높은 순)와 글자에서 찾은 품목 이름 */
    public static class Result {
        public final List<String> candidates;
        public final String category;     // 못 찾으면 null

        Result(List<String> candidates, String category) {
            this.candidates = candidates;
            this.category = category;
        }
    }

    public static Result extract(String recognizedText) {
        if (recognizedText == null) return new Result(new ArrayList<>(), null);

        Map<String, Integer> scored = new LinkedHashMap<>();
        // 공백 · 줄바꿈 · 괄호 · 콜론 · 쉼표 등으로 나눈다 (하이픈 · 슬래시 · 점은 모델명에 쓰여서 남긴다)
        for (String raw : recognizedText.split("[\\s()\\[\\]{}:;,'\"·|]+")) {
            String token = normalize(raw);
            if (!isModelLike(token)) continue;
            int score = score(token);
            Integer prev = scored.get(token);
            if (prev == null || prev < score) scored.put(token, score);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(scored.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries) {
            candidates.add(e.getKey());
            if (candidates.size() == 5) break;    // 후보는 최대 5개
        }
        return new Result(candidates, Categories.detect(recognizedText));
    }

    /** 앞뒤 기호를 떼고 대문자로. 글자 인식이 자주 헷갈리는 모양은 그대로 둔다 (사용자가 고칠 수 있음) */
    static String normalize(String raw) {
        String t = raw.trim().toUpperCase(Locale.ROOT);
        t = t.replaceAll("^[\\-_/.]+|[\\-_/.]+$", "");
        // "모델명AF17B6474" 처럼 한글이 붙어 있으면 한글 뒤쪽만 쓴다
        t = t.replaceAll("^.*[가-힣]", "");
        return t;
    }

    /** 다른 화면에서 쓰는 이름 (검색어가 모델명처럼 생겼는지) */
    public static boolean looksLikeModel(String text) {
        return text != null && isModelLike(normalize(text));
    }

    /**
     * 검색 결과가 없을 때 넓혀 볼 검색어들. 국내 가전 모델명은 뒤쪽 글자가
     * 색상 · 판매처 · 설치 구성을 뜻하는 경우가 많아서, 뒤에서 2글자씩 줄여 본다 (최소 6자).
     * 예: FQ18D7DWAN → [FQ18D7DW, FQ18D7]
     */
    public static java.util.List<String> shorterQueries(String model) {
        java.util.List<String> result = new ArrayList<>();
        String t = normalize(model);
        for (int len = t.length() - 2; len >= 6 && result.size() < 2; len -= 2) {
            String cut = t.substring(0, len).replaceAll("[\\-_/.]+$", "");
            if (cut.length() >= 6 && !result.contains(cut)) result.add(cut);
        }
        return result;
    }

    static boolean isModelLike(String t) {
        if (!ALLOWED.matcher(t).matches()) return false;
        if (!HAS_LETTER.matcher(t).find() || !HAS_DIGIT.matcher(t).find()) return false;
        if (UNIT_ONLY.matcher(t).matches()) return false;
        if (DATE_LIKE.matcher(t).matches()) return false;
        if (t.startsWith("HTTP") || t.startsWith("WWW")) return false;
        return true;
    }

    static int score(String t) {
        int letters = 0, digits = 0;
        for (char c : t.toCharArray()) {
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
        }
        int score = Math.min(t.length(), 14) * 2;              // 길수록 모델명일 가능성이 높다
        score += Math.min(letters, digits) * 3;                // 영문과 숫자가 고르게 섞일수록
        if (t.contains("-")) score += 4;                       // CRP-LHTR0610FW 같은 형태
        if (Character.isLetter(t.charAt(0))) score += 3;       // 대부분 영문으로 시작
        return score;
    }
}
