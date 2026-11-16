package com.example.gajeontiming.scan;

import com.example.gajeontiming.model.Categories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 모델명 검색에서 본품을 못 찾았을 때, 걸러낸 소모품 제목에서 브랜드 · 품목을 뽑아
 * 다음 검색어를 제안한다 (순수 Java, JUnit 테스트 가능).
 * 예: 걸러낸 제목에 "LG … 에어컨 극세 필터"가 많으면 → ["LG 에어컨 FQ18", "LG 에어컨"]
 */
public final class SearchSuggester {

    /** 국내에서 많이 팔리는 가전 브랜드 (제목에서 이 이름을 찾는다) */
    private static final String[][] BRANDS = {
            {"LG", "LG"}, {"엘지", "LG"}, {"삼성", "삼성"}, {"SAMSUNG", "삼성"},
            {"위니아", "위니아"}, {"캐리어", "캐리어"}, {"쿠쿠", "쿠쿠"}, {"쿠첸", "쿠첸"},
            {"다이슨", "다이슨"}, {"DYSON", "다이슨"}, {"로보락", "로보락"}, {"에코백스", "에코백스"},
            {"드리미", "드리미"}, {"모바", "모바"}, {"SK매직", "SK매직"}, {"코웨이", "코웨이"},
            {"필립스", "필립스"}, {"테팔", "테팔"}, {"신일", "신일"}, {"보국", "보국"}
    };

    private SearchSuggester() { }

    /**
     * @param model         사용자가 찾은 모델명 (예: FQ18D7DWAN)
     * @param knownCategory 이미 알던 품목 (없으면 null)
     * @param removedTitles 소모품으로 걸러낸 상품 제목들
     */
    public static List<String> suggest(String model, String knownCategory, List<String> removedTitles) {
        String brand = mostFrequent(removedTitles, true);
        String category = knownCategory != null ? knownCategory : mostFrequent(removedTitles, false);

        List<String> result = new ArrayList<>();
        String prefix = modelPrefix(model);
        if (brand != null && category != null && prefix != null) add(result, brand + " " + category + " " + prefix);
        if (brand != null && category != null) add(result, brand + " " + category);
        if (brand == null && category != null && prefix != null) add(result, category + " " + prefix);
        if (category != null) add(result, category);
        return result;
    }

    /** 모델명 앞 4글자 (보통 제품군 · 평형을 뜻함). 예: FQ18D7DWAN → FQ18 */
    static String modelPrefix(String model) {
        if (model == null) return null;
        String t = model.trim().toUpperCase(Locale.ROOT);
        return t.length() >= 6 ? t.substring(0, 4) : null;
    }

    /** 제목들에서 가장 많이 나온 브랜드(brand=true) 또는 품목(brand=false). 없으면 null */
    static String mostFrequent(List<String> titles, boolean brand) {
        if (titles == null || titles.isEmpty()) return null;
        Map<String, Integer> count = new HashMap<>();
        for (String title : titles) {
            String found = brand ? findBrand(title) : Categories.detect(title);
            if (found != null) count.merge(found, 1, Integer::sum);
        }
        String best = null;
        int max = 0;
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            if (e.getValue() > max) {
                best = e.getKey();
                max = e.getValue();
            }
        }
        return best;
    }

    static String findBrand(String title) {
        if (title == null) return null;
        String t = title.toUpperCase(Locale.ROOT);
        for (String[] b : BRANDS) {
            if (t.contains(b[0])) return b[1];
        }
        return null;
    }

    private static void add(List<String> list, String s) {
        if (!list.contains(s)) list.add(s);
    }
}
