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

    // ── 서버 backend/tests/test_accessory_filter.py와 같은 사례 (이름에서 test_만 뺐다) ──

    /** 서버 DB 점검(scripts/audit_prices.py)에서 소모품으로 잘못 잡았던 실제 본품 17개 */
    @Test
    public void DB_점검에서_잘못_걸렸던_본품() {
        Object[][] real = {
                {"삼성전자 AI Q9000 AF60F17D11GRS", 1475000},
                {"삼성전자 뮤직 스튜디오5 HW-LS50H/ LS51H", 392660},
                {"삼성전자 3.1.2ch 사운드바 Q600H HW-Q600H/KR", 699000},
                {"창홍 스마트TV 4K CH431UHD Google M9GPW1 120Hz 구글 5", 419000},
                {"삼성전자 비스포크 AI DW80H73Y1UZ + NZ63DB607CFH", 1499000},
                {"삼성전자 비스포크 키친핏 RR70H39G10 + RZ70H32UN0 + RQ70H32S10", 2609000},
                {"LG전자 오브제컬렉션 컨버터블 X323MEF + Y323MEF + Z323MEF", 2436470},
                {"다이슨 에어랩 오리진+ 멀티 스타일러 앤 드라이어 + 라운드볼륨브러쉬", 421700},
                {"제우스 빔프로젝터 4K 오토 단초점 가정용 ZEUS L1300+전용가방", 339000},
                {"[예약판매]삼성 비스포크 식기세척기 인덕션세트 14인용+3구 DW80H73 NZ63DB607CFH", 2099000},
                {"루니스 히팅패드 4면 D1905Q", 47900},
                {"신일 전기히터 사무실 발난로 파티션히터 다리 온열기 접이식 난로 히팅패드", 57900},
                {"LG전자 휘센 오브제컬렉션 듀얼호스 PQ08FDWBS", 791540},
                {"신일 선풍기 아기 무소음 스탠드형 거실 리모컨", 99000},
                {"스탠드 선풍기 가정용 저소음 아기방 리모컨 초미풍 5엽 35cm", 49700},
                {"한경희 저소음 스탠드 선풍기 가정용 거실 사무실 원룸 리모컨 발터치 14인치", 39900},
                {"삼성 사운드바 9.1.4ch HW-Q930H/KR Q 시리즈 고품질 사운드", 1390000},
        };
        for (Object[] r : real) {
            String name = (String) r[0];
            assertFalse(name, AccessoryFilter.isAccessory(name, (Integer) r[1]));
        }
    }

    @Test
    public void 진짜_소모품은_여전히_거른다() {
        assertTrue(AccessoryFilter.isAccessory("로보락 S7 MaxV Ultra, S8 Plus, S8 Pro Ultra 호환 더스트백", 7150));
        assertTrue(AccessoryFilter.isAccessory("로보락 S9 MaxV Ultra 전용 울트라도크 거름망", 8300));
        assertTrue(AccessoryFilter.isAccessory("로보락 S9 MaxV Ultra 호환 물걸레 + 사이드 물걸레 세트", 23410));
        assertTrue(AccessoryFilter.isAccessory("신일 선풍기 리모컨", 9900));   // 품목 이름이 있어도 싸고 리모컨으로 끝남
        assertTrue(AccessoryFilter.isAccessory("에어컨 필터 1+1", 12000));
    }

    @Test
    public void 묶음_상품은_더하기_앞_본품만_본다() {
        assertEquals("삼성전자 비스포크 AI DW80H73Y1UZ",
                AccessoryFilter.mainPart("삼성전자 비스포크 AI DW80H73Y1UZ + NZ63DB607CFH"));
        assertEquals("제우스 빔프로젝터 ZEUS L1300",
                AccessoryFilter.mainPart("제우스 빔프로젝터 ZEUS L1300+전용가방"));
        // 양쪽이 모두 숫자인 '+'("1+1")는 묶음이 아니다
        assertEquals("에어컨 필터 1+1", AccessoryFilter.mainPart("에어컨 필터 1+1"));
        // "14인용+3구"는 '+' 앞이 숫자가 아니라서 서버 정규식과 똑같이 나뉜다
        // (앞부분 "식기세척기 14인용"에 소모품 단어가 없어서 본품 판정에는 영향 없음)
        assertEquals("식기세척기 14인용", AccessoryFilter.mainPart("식기세척기 14인용+3구"));
    }
}
