package com.example.gajeontiming.model;

/** 품목 목록. 서버의 카테고리 규칙 이름과 똑같이 써야 필터 · 통계가 맞게 적용된다. */
public final class Categories {

    /** F-03 홈 화면에 칩으로 보여줄 품목 */
    public static final String[] ALL = {
            "에어컨", "냉장고", "세탁기", "건조기", "공기청정기", "로봇청소기", "식기세척기",
            "TV", "세탁건조기", "에어프라이어", "전기밥솥", "전자레인지", "가습기"
    };

    /**
     * 제품명에서 품목을 찾을 때 쓰는 목록.
     * 긴 이름을 먼저 검사해야 "세탁건조기"가 "건조기"로 잘못 잡히지 않는다.
     */
    private static final String[] DETECT_ORDER = {
            "세탁건조기", "블루투스 스피커", "헤어스타일러", "헤어드라이어", "공기청정기", "로봇청소기",
            "식기세척기", "에어프라이어", "전자레인지", "전기밥솥", "전기히터", "전기포트",
            "커피머신", "사운드바", "프로젝터", "에어컨", "냉장고", "세탁기", "건조기",
            "가습기", "제습기", "선풍기", "믹서기", "TV"
    };

    private Categories() { }

    /** 제품명 · 검색어에 들어 있는 품목 이름. 없으면 null */
    public static String detect(String text) {
        if (text == null) return null;
        for (String name : DETECT_ORDER) {
            if (text.contains(name)) return name;
        }
        return null;
    }
}
