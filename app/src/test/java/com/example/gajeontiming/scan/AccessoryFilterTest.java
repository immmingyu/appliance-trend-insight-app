package com.example.gajeontiming.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 모델명 검색에 딸려 나오는 소모품 거르기 단위 테스트 */
public class AccessoryFilterTest {

    @Test
    public void 호환_교체용_상품은_소모품이다() {
        assertTrue(AccessoryFilter.isAccessory("FQ18D7 호환 에어컨 필터 2개입"));
        assertTrue(AccessoryFilter.isAccessory("삼성 무풍 에어컨 교체용 극세필터"));
        assertTrue(AccessoryFilter.isAccessory("AX034 공기청정기 정품필터"));
    }

    @Test
    public void 품목_이름_없이_부속품_단어만_있으면_소모품이다() {
        assertTrue(AccessoryFilter.isAccessory("FQ18D7DWAN 리모컨"));
        assertTrue(AccessoryFilter.isAccessory("FQ18D7 스탠드 커버 방진덮개"));
        assertTrue(AccessoryFilter.isAccessory("P70 물걸레 패드 10매"));
    }

    /** FQ18D7DWAN 스캔 검색에서 실제로 빠져나왔던 상품들 (품목 이름 '에어컨'이 들어 있음) */
    @Test
    public void 품목_이름이_있어도_에어컨_필터는_소모품이다() {
        assertTrue(AccessoryFilter.isAccessory(
                "LG FQ19D7DWAN FQ18VCDRAN FQ18DBDNAN 스탠드 에어컨 극세 필터"));
        assertTrue(AccessoryFilter.isAccessory(
                "LG 에어컨 스탠드 극세 필터 ABQ30198703"));
        assertTrue(AccessoryFilter.isAccessory(
                "LG 정품 FQ18VCDRAN.AKOR FQ19D7DWAN.AKOR 에어컨 스탠드 극세 필터"));
    }

    @Test
    public void 소모품_단어로_끝나거나_싸면_소모품이다() {
        assertTrue(AccessoryFilter.isAccessory("삼성 에어컨 리모컨 2개입"));            // 수량 표시를 떼면 '리모컨'으로 끝남
        assertTrue(AccessoryFilter.isAccessory("삼성 에어컨 실외기 커버 FQ18", 19900)); // 5만 원 미만 + 커버
    }

    @Test
    public void 본품은_거르지_않는다() {
        assertFalse(AccessoryFilter.isAccessory("삼성전자 비스포크 AI 무풍클래식 FQ18D7DWAN", 2590000));
        assertFalse(AccessoryFilter.isAccessory("LG전자 휘센 오브제컬렉션 FQ18HDKHA2", 2190000));
        assertFalse(AccessoryFilter.isAccessory("모바 P70 Pro Ultra 로봇청소기", 550900));
        // 본품 이름(공기청정기)이 있고 모델명으로 끝나면 '필터'가 들어가도 본품
        assertFalse(AccessoryFilter.isAccessory("삼성 블루스카이 공기청정기 헤파필터 AX060", 459000));
        // 같은 모델의 변형 표기는 모델명 1개로 센다
        assertFalse(AccessoryFilter.isAccessory("LG전자 퓨리케어 360 AS354NS4A (AS354NS4AM)", 699000));
    }

    @Test
    public void 도우미_규칙() {
        assertEquals("필터", AccessoryFilter.lastMeaningfulWord("에어컨 스탠드 극세 필터 2개입"));
        assertEquals(3, AccessoryFilter.distinctModelCount("LG FQ19D7DWAN FQ18VCDRAN FQ18DBDNAN 필터"));
        assertEquals(1, AccessoryFilter.distinctModelCount("퓨리케어 AS354NS4A (AS354NS4AM)"));
    }
}
