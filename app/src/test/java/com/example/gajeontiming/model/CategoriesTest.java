package com.example.gajeontiming.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CategoriesTest {

    @Test
    public void 새_품목_찾기() {
        assertEquals("의류관리기", Categories.detect("LG 스타일러 의류관리기 S5BB"));
        assertEquals("신발관리기", Categories.detect("LG 슈케어 신발관리기"));
        assertEquals("정수기", Categories.detect("코웨이 아이콘 정수기"));
        assertEquals("오븐", Categories.detect("삼성 광파오븐"));
    }

    @Test
    public void 전기레인지와_전자레인지는_다른_품목() {
        assertEquals("전기레인지", Categories.detect("SK매직 3구 인덕션 전기레인지"));
        assertEquals("전자레인지", Categories.detect("삼성 전자레인지 23L"));
    }

    @Test
    public void 긴_이름_먼저() {
        assertEquals("세탁건조기", Categories.detect("LG 트롬 세탁건조기"));
        assertEquals("전자레인지", Categories.detect("전자레인지 겸용 오븐"));
    }

    @Test
    public void 품목_단어가_없으면_null() {
        assertNull(Categories.detect("LG 뉴 스타일러"));
        assertNull(Categories.detect(null));
    }
}
