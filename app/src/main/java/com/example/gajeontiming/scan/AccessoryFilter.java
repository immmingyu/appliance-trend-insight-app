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
            "브러시", "브러쉬", "배터리", "어댑터", "노즐", "트레이"
    };

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
        if (title == null) return false;
        String t = title.toUpperCase(Locale.ROOT);

        // 1) 강한 신호
        for (String w : STRONG) {
            if (t.contains(w)) return true;
        }
        // 2) 호환 모델명 나열
        if (distinctModelCount(title) >= 2) return true;
        // 3) 소모품 단어로 끝남
        if (containsWeak(lastMeaningfulWord(t))) return true;

        boolean hasWeak = containsWeak(t);
        // 4) 싼데 소모품 단어가 있음
        if (hasWeak && price > 0 && price < CHEAP) return true;
        // 5) 소모품 단어가 있고 본품 이름이 없음
        return hasWeak && Categories.detect(title) == null;
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
