package com.example.gajeontiming.scan;

import com.example.gajeontiming.model.Categories;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 모델명으로 검색했을 때 딸려 나오는 소모품 · 부속품을 거른다 (순수 Java, JUnit 테스트 가능).
 * 서버는 품목 이름을 알 때만 소모품을 거르므로, 품목을 모르는 검색에서 앱이 대신 거른다.
 *
 * 판단 규칙 (위에서부터 하나라도 맞으면 소모품)
 *   1) "호환 · 교체용 · 리필 · 정품필터 · 극세 필터" 같은 말이 있다
 *   2) 서로 다른 모델명이 2개 이상 있다 → "이 모델들에 맞는 부품" (본품 이름에는 자기 모델명 하나)
 *   3) 제품명이 소모품 단어로 끝난다 (예: "… 스탠드 극세 필터", "… 리모컨 2개입")
 *      본품은 보통 모델명으로 끝난다 (예: "공기청정기 헤파필터 AX060")
 *   4) 5만 원 미만인데 소모품 단어가 있다
 *   5) 소모품 단어가 있고 제품명에 품목 이름(에어컨 등)이 없다
 *
 * 처음 규칙은 5번만 있었는데, "LG 에어컨 스탠드 극세 필터"처럼 품목 이름을 넣어 파는
 * 소모품이 빠져나와서 1~4번을 추가했다 (FQ18D7DWAN 스캔 검색에서 발견).
 *
 * 본품을 소모품으로 잘못 거르지 않게 (서버 DB 점검에서 실제로 걸렸던 사례)
 *   - "A + B" 묶음 상품은 '+' 앞의 본품만 보고 판단한다 (규칙 2 · 3 · 5). 강한 신호(1)는 묶음 전체에서 본다
 *     양쪽이 모두 숫자인 '+'("1+1")만 묶음이 아니다. "14인용+3구"는 묶음으로 나뉜다 (의도된 동작)
 *   - 모델명이 2개여도 소모품 단어가 없고 싸지 않으면 본품이다 (예: "AI Q9000 AF60F17D11GRS")
 *   - "히팅패드 · 듀얼호스"처럼 소모품 단어가 들어간 제품 이름은 소모품 단어로 보지 않는다
 *   - 품목 이름이 있고 3만 원 이상인 제품의 "리모컨"은 기능 설명이다 (예: "스탠드 선풍기 가정용 리모컨")
 *
 * 서버(backend/app/utils/accessory_filter.py)와 같은 규칙이다. 한쪽을 고치면 다른 쪽도 같이 고칠 것.
 */
public final class AccessoryFilter {

    /** 이 말이 있으면 거의 확실히 소모품 */
    private static final String[] STRONG = {
            "호환", "교체용", "교체 필터", "교체필터", "리필", "정품필터", "정품 필터",
            "대체용", "전용 필터", "전용필터", "극세 필터", "극세필터"
    };

    /** 본품 이름에는 잘 안 들어가는 소모품 · 부속품 단어 */
    private static final String[] WEAK = {
            "필터", "커버", "덮개", "거치대", "받침대", "브라켓", "케이블", "리모컨", "호스",
            "부품", "소모품", "세정제", "세척제", "스티커", "패드", "걸레", "먼지봉투",
            "브러시", "브러쉬", "배터리", "어댑터", "노즐", "트레이",
            // 로봇청소기 · 청소기 소모품 (예: "로보락 S9 MaxV Ultra 전용 울트라도크 거름망")
            "거름망", "필터망", "더스트백", "물걸레포", "전용"
    };

    /** 소모품 단어가 들어 있지만 그 자체가 본품 이름인 말 → 소모품 단어 검사 전에 지운다 */
    private static final String[] PRODUCT_COMPOUNDS = {
            "히팅패드", "전기패드", "온열패드", "쿨링패드", "듀얼호스", "싱글호스"
    };

    /** 품목 이름과 같이 있고 3만 원 이상이면 본품의 기능 설명인 말 (예: "스탠드 선풍기 리모컨" 99,000원) */
    private static final String[] FEATURE_WORDS = { "리모컨" };
    private static final int FEATURE_MIN_PRICE = 30000;

    /** 묶음 상품의 '+' (양쪽이 모두 숫자인 "1+1"만 제외. "14인용+3구"는 묶음으로 나뉜다) */
    private static final Pattern BUNDLE_PLUS = Pattern.compile("(?<![0-9])\\+|\\+(?![0-9])");

    /** 제품명 끝에 붙는 수량 표시: 2개입, 10매, 1EA, 3P, 세트, 1+1 … */
    private static final Pattern QUANTITY = Pattern.compile(
            "^(\\d+(개입|개|매|EA|P|팩|세트|SET|롤|장)|세트|SET|\\d\\+\\d|\\(.*\\)|\\[.*\\])$");

    private static final int CHEAP = 50000;

    private AccessoryFilter() { }

    public static boolean isAccessory(String title) {
        return isAccessory(title, 0);
    }

    /** price: 상품 가격 (모르면 0) */
    public static boolean isAccessory(String title, long price) {
        if (title == null || title.isEmpty()) return false;
        String t = title.toUpperCase(Locale.ROOT);

        // 1) 강한 신호 (묶음 전체에서)
        for (String w : STRONG) {
            if (t.contains(w)) return true;
        }

        // 묶음이면 '+' 앞 본품만 본다 (예: "DW80H73Y1UZ + NZ63DB607CFH", "ZEUS L1300+전용가방")
        String main = mainPart(title);
        String m = main.toUpperCase(Locale.ROOT);
        boolean hasCategory = Categories.detect(main) != null;
        boolean featureOk = hasCategory && price >= FEATURE_MIN_PRICE;
        boolean hasWeak = containsWeak(weakText(m, featureOk));
        boolean cheap = price > 0 && price < CHEAP;

        // 2) 호환 모델명 나열 (모델명이 2개여도 소모품 단어가 없고 싸지 않으면 본품)
        if (distinctModelCount(main) >= 2 && (hasWeak || cheap)) return true;
        // 3) 소모품 단어로 끝남
        if (containsWeak(weakText(lastMeaningfulWord(m), featureOk))) return true;
        // 4) 싼데 소모품 단어가 있음
        if (hasWeak && cheap) return true;
        // 5) 소모품 단어가 있고 본품 이름이 없음
        return hasWeak && !hasCategory;
    }

    /** 묶음 상품이면 첫 '+' 앞의 본품 부분. 묶음이 아니거나 앞부분이 비면 제목 그대로 */
    static String mainPart(String title) {
        String[] parts = BUNDLE_PLUS.split(title);
        String main = parts.length > 0 ? parts[0].trim() : "";
        return main.isEmpty() ? title : main;
    }

    /** 소모품 단어 검사에서 빼야 할 말(본품 이름 · 기능 설명)을 지운 문자열 */
    static String weakText(String text, boolean featureOk) {
        for (String w : PRODUCT_COMPOUNDS) text = text.replace(w, " ");
        if (featureOk) {
            for (String w : FEATURE_WORDS) text = text.replace(w, " ");
        }
        return text;
    }

    private static boolean containsWeak(String text) {
        if (text == null) return false;
        for (String w : WEAK) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    /** 수량 표시를 뗀 마지막 단어. 예: "에어컨 스탠드 극세 필터 2개입" → "필터" */
    static String lastMeaningfulWord(String upperTitle) {
        String[] words = upperTitle.trim().split("\\s+");
        for (int i = words.length - 1; i >= 0; i--) {
            if (!QUANTITY.matcher(words[i]).matches()) return words[i];
        }
        return "";
    }

    /**
     * 제품명 속 서로 다른 모델명 개수.
     * 앞 6글자가 같으면 같은 모델의 변형으로 본다 (예: AS354NS4A와 AS354NS4AM은 1개).
     */
    static int distinctModelCount(String title) {
        List<String> prefixes = new ArrayList<>();
        for (String raw : title.split("[\\s()\\[\\]{},/]+")) {
            String token = ModelNameExtractor.normalize(raw);
            if (!ModelNameExtractor.isModelLike(token)) continue;
            String prefix = token.length() > 6 ? token.substring(0, 6) : token;
            if (!prefixes.contains(prefix)) prefixes.add(prefix);
        }
        return prefixes.size();
    }
}
