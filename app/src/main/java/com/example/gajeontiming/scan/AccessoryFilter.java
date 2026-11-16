package com.example.gajeontiming.scan;

import com.example.gajeontiming.model.Categories;

import java.util.Locale;

/**
 * 모델명으로 검색했을 때 딸려 나오는 소모품 · 부속품을 거른다 (순수 Java, JUnit 테스트 가능).
 * 서버는 품목 이름을 알 때만 소모품을 거르므로, 품목을 모르는 검색에서 앱이 대신 거른다.
 *
 * 판단 규칙
 *   - "호환 · 교체용 · 리필 · 정품 필터" 같은 말이 있으면 → 소모품 (제품명에 품목 이름이 있어도)
 *   - "필터 · 커버 · 리모컨 · 부품 …"이 있고 제품명에 품목 이름(에어컨 등)이 없으면 → 소모품
 *   - 그 밖에는 본품으로 본다
 */
public final class AccessoryFilter {

    /** 이 말이 있으면 거의 확실히 소모품 */
    private static final String[] STRONG = {
            "호환", "교체용", "교체 필터", "리필", "정품필터", "정품 필터", "대체용", "전용 필터"
    };

    /** 본품 이름에는 잘 안 들어가는 소모품 · 부속품 단어 */
    private static final String[] WEAK = {
            "필터", "커버", "덮개", "거치대", "받침대", "브라켓", "케이블", "리모컨", "호스",
            "부품", "소모품", "세정제", "세척제", "스티커", "패드", "걸레", "먼지봉투",
            "브러시", "브러쉬", "배터리", "어댑터", "노즐", "트레이"
    };

    private AccessoryFilter() { }

    public static boolean isAccessory(String title) {
        if (title == null) return false;
        String t = title.toUpperCase(Locale.ROOT);
        for (String w : STRONG) {
            if (t.contains(w)) return true;
        }
        if (Categories.detect(title) != null) return false;   // "에어컨" 등 본품 이름이 있으면 본품
        for (String w : WEAK) {
            if (t.contains(w)) return true;
        }
        return false;
    }
}
