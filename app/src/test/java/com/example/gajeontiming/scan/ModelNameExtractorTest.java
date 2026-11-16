package com.example.gajeontiming.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * F-16 모델명 판별 단위 테스트 (안드로이드 없이 PC에서 바로 실행).
 * Android Studio: 이 파일을 오른쪽 클릭 > Run 'ModelNameExtractorTest'
 */
public class ModelNameExtractorTest {

    @Test
    public void 에어컨_가격표에서_모델명과_품목을_찾는다() {
        ModelNameExtractor.Result r = ModelNameExtractor.extract(
                "삼성 비스포크 무풍에어컨 갤러리\n모델명: AF17B6474TZS\n정가 2,890,000원\n220V 60Hz 17평형");
        assertEquals("AF17B6474TZS", r.candidates.get(0));
        assertEquals("에어컨", r.category);
    }

    @Test
    public void 괄호_안의_모델명도_찾는다() {
        ModelNameExtractor.Result r = ModelNameExtractor.extract(
                "LG 디오스 오브제컬렉션 냉장고 (M874GBB031)\n용량 870L 1등급");
        assertEquals("M874GBB031", r.candidates.get(0));
        assertEquals("냉장고", r.category);
    }

    @Test
    public void 하이픈이_있는_모델명을_찾는다() {
        ModelNameExtractor.Result r = ModelNameExtractor.extract(
                "쿠쿠 IH전기압력밥솥 CRP-LHTR0610FW 6인용\n소비전력 1,090W");
        assertEquals("CRP-LHTR0610FW", r.candidates.get(0));
    }

    @Test
    public void 한글이_붙어_있어도_모델명만_떼어낸다() {
        ModelNameExtractor.Result r = ModelNameExtractor.extract("모델명AF17B6474TZS 에어컨");
        assertEquals("AF17B6474TZS", r.candidates.get(0));
    }

    @Test
    public void 전화번호_무게_전력_주소는_모델명이_아니다() {
        ModelNameExtractor.Result r = ModelNameExtractor.extract(
                "www.samsung.com 고객센터 1588-3366 5KG 1500W 220V 2026.11.12");
        assertTrue(r.candidates.isEmpty());
        assertNull(r.category);
    }

    @Test
    public void 모델명_규칙() {
        assertTrue(ModelNameExtractor.isModelLike("AF17B6474TZS"));
        assertFalse(ModelNameExtractor.isModelLike("ABCDEF"));      // 숫자 없음
        assertFalse(ModelNameExtractor.isModelLike("1234567"));     // 영문 없음
        assertFalse(ModelNameExtractor.isModelLike("17KG"));        // 너무 짧고 단위
        assertFalse(ModelNameExtractor.isModelLike("1500KWH"));     // 단위가 붙은 숫자
    }

    @Test
    public void 결과가_없을_때_뒷자리를_줄여_본다() {
        assertEquals(java.util.Arrays.asList("FQ18D7DW", "FQ18D7"),
                ModelNameExtractor.shorterQueries("FQ18D7DWAN"));
        assertEquals(java.util.Arrays.asList("CRP-LHTR0610", "CRP-LHTR06"),
                ModelNameExtractor.shorterQueries("CRP-LHTR0610FW"));
        assertTrue(ModelNameExtractor.shorterQueries("HW500").isEmpty());   // 6자 미만은 줄이지 않음
    }
}
