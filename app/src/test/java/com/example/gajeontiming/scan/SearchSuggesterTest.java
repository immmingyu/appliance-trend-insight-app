package com.example.gajeontiming.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 본품을 못 찾았을 때 다음 검색어 제안 단위 테스트 */
public class SearchSuggesterTest {

    @Test
    public void 걸러낸_소모품_제목에서_브랜드와_품목을_뽑는다() {
        List<String> removed = Arrays.asList(
                "LG FQ19D7DWAN FQ18VCDRAN FQ18DBDNAN 스탠드 에어컨 극세 필터",
                "LG 에어컨 스탠드 극세 필터 ABQ30198703",
                "LG 정품 FQ18VCDRAN.AKOR FQ19D7DWAN.AKOR 에어컨 스탠드 극세 필터");
        assertEquals(Arrays.asList("LG 에어컨 FQ18", "LG 에어컨", "에어컨"),
                SearchSuggester.suggest("FQ18D7DWAN", null, removed));
    }

    @Test
    public void 이미_알던_품목이_있으면_그걸_쓴다() {
        List<String> removed = Collections.singletonList("삼성 무풍 리모컨 FQ18");
        assertEquals(Arrays.asList("삼성 에어컨 FQ18", "삼성 에어컨", "에어컨"),
                SearchSuggester.suggest("FQ18D7DWAN", "에어컨", removed));
    }

    @Test
    public void 아무_힌트도_없으면_제안하지_않는다() {
        assertTrue(SearchSuggester.suggest("ZZ123456", null, Collections.emptyList()).isEmpty());
    }
}
