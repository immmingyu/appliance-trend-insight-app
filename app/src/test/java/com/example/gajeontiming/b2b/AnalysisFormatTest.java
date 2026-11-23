package com.example.gajeontiming.b2b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AnalysisFormatTest {

    @Test
    public void 원_표기() {
        assertEquals("1,234,567원", AnalysisFormat.won(1234567.0));
        assertEquals("990,000원", AnalysisFormat.won(989999.6));
        assertNull(AnalysisFormat.won(null));
        assertNull(AnalysisFormat.won(0.0));
    }

    @Test
    public void 출시월() {
        assertEquals("2025.06", AnalysisFormat.month("2025-06"));
        assertEquals("2025년 6월", AnalysisFormat.month("2025년 6월"));
        assertNull(AnalysisFormat.month(" "));
        assertNull(AnalysisFormat.month(null));
    }

    @Test
    public void 그래프_날짜() {
        assertEquals("11.22", AnalysisFormat.shortDate("2026-11-22"));
        assertEquals("11.22", AnalysisFormat.shortDate("2026-11-22T00:00:00"));
        assertEquals("25.03", AnalysisFormat.shortDate("2025-03"));
        assertEquals("25.03", AnalysisFormat.shortDate("202503"));
        assertEquals("", AnalysisFormat.shortDate(null));
    }

    @Test
    public void 에너지_정보() {
        assertEquals("에너지 1등급 · 월 전기요금 약 12,300원",
                AnalysisFormat.energy("1", 12300.0, null));
        assertEquals("월 전기요금 약 5,000원 (하루 8시간 기준)",
                AnalysisFormat.energy(null, 5000.0, "하루 8시간 기준"));
        assertEquals("에너지 2등급", AnalysisFormat.energy("2", null, null));
        assertNull(AnalysisFormat.energy(null, null, null));
    }

    @Test
    public void 품목_단어_찾기() {
        assertEquals("냉장고", AnalysisFormat.detectCategoryHint("비스포크", "삼성 비스포크 4도어 냉장고 RF85"));
        assertEquals("김치냉장고", AnalysisFormat.detectCategoryHint("딤채 김치냉장고"));
        assertEquals("로봇청소기", AnalysisFormat.detectCategoryHint(null, "로보락 로봇청소기 S8"));
        assertNull(AnalysisFormat.detectCategoryHint("비스포크", "삼성전자 비스포크 RF85DB9"));
        assertEquals("의류관리기", AnalysisFormat.detectCategoryHint("삼성 의류관리기 에어드레서"));
        assertEquals("헤어스타일러", AnalysisFormat.detectCategoryHint("다이슨 에어랩 헤어스타일러"));
        // 전기레인지(쿡탑)와 전자레인지는 다른 품목
        assertEquals("전기레인지", AnalysisFormat.detectCategoryHint("SK매직 3구 전기레인지"));
        assertEquals("전자레인지", AnalysisFormat.detectCategoryHint("삼성 전자레인지 23L"));
        assertEquals("정수기", AnalysisFormat.detectCategoryHint("코웨이 아이콘 정수기"));
        assertEquals("신발관리기", AnalysisFormat.detectCategoryHint("LG 슈케어 신발관리기"));
    }

    @Test
    public void 칩_이름() {
        assertEquals("인덕션", AnalysisFormat.chipLabel("전기레인지"));
        assertEquals("오븐", AnalysisFormat.chipLabel("오븐"));
    }
}
