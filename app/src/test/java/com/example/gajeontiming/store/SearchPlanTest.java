package com.example.gajeontiming.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** 매장 모드 재검색 순서 단위 테스트 */
public class SearchPlanTest {

    @Test
    public void 모델명과_품목이_있으면_줄이기를_먼저_하고_품목_없이는_마지막() {
        List<SearchPlan.Attempt> plan = SearchPlan.build("FQ18D7DWAN", "에어컨");
        assertEquals(4, plan.size());
        assertEquals("FQ18D7DWAN", plan.get(0).query);
        assertEquals("에어컨", plan.get(0).category);
        assertEquals("FQ18D7DW", plan.get(1).query);
        assertEquals("FQ18D7", plan.get(2).query);
        assertEquals("FQ18D7DWAN", plan.get(3).query);
        assertNull(plan.get(3).category);
    }

    @Test
    public void 일반_검색어는_줄이지_않는다() {
        List<SearchPlan.Attempt> plan = SearchPlan.build("LG 에어컨", null);
        assertEquals(1, plan.size());
    }

    @Test
    public void 빈_검색어는_계획이_없다() {
        assertTrue(SearchPlan.build("  ", "에어컨").isEmpty());
        assertTrue(SearchPlan.build(null, null).isEmpty());
    }
}
