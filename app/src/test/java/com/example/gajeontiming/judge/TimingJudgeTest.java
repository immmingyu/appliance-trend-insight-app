package com.example.gajeontiming.judge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 확장 기능: 앱 자체 구매 판단(가전무쌍 점수) 단위 테스트 */
public class TimingJudgeTest {

    /** 30일 동안 start에서 end까지 고르게 바뀌는 가격 */
    private static long[] line(long start, long end, int days) {
        long[] p = new long[days];
        for (int i = 0; i < days; i++) p[i] = start + (end - start) * i / (days - 1);
        return p;
    }

    private static boolean hasReason(TimingJudge.Result r, String word) {
        for (TimingJudge.Reason reason : r.reasons) if (reason.text.contains(word)) return true;
        return false;
    }

    @Test
    public void 평균보다_싸고_30일_최저가면_지금_사세요() {
        long[] prices = new long[30];
        for (int i = 0; i < 30; i++) prices[i] = 1_000_000;
        prices[29] = 850_000;                                    // 오늘만 15% 할인
        TimingJudge.Result r = TimingJudge.judge(prices, 850_000, 20.0, null);
        assertEquals("지금 사세요", r.grade);
        assertTrue(r.score >= 75);
        assertTrue(hasReason(r, "가장 싼 가격"));
        assertTrue(r.reliable);
    }

    @Test
    public void 평균보다_비싸고_범위_위쪽이면_기다리세요() {
        long[] prices = line(1_000_000, 1_000_000, 30);
        prices[0] = 900_000;                                     // 예전에 싸게 팔던 날이 있었음
        TimingJudge.Result r = TimingJudge.judge(prices, 1_150_000, 2.0, null);
        assertEquals("기다리세요", r.grade);
        assertTrue(hasReason(r, "비싸요"));
        assertTrue(hasReason(r, "할인 전"));
    }

    @Test
    public void 계속_내려가는_중이면_조금_더_기다리라는_근거가_붙는다() {
        TimingJudge.Result r = TimingJudge.judge(line(1_200_000, 1_000_000, 30), 1_000_000, null, null);
        assertTrue(hasReason(r, "내려가는 중"));
    }

    @Test
    public void 오르는_중이고_관심이_늘면_오르기_전에_사라는_근거가_붙는다() {
        TimingJudge.Result r = TimingJudge.judge(line(800_000, 1_000_000, 30), 1_000_000, null, 35.0);
        assertTrue(hasReason(r, "오르는 중"));
        assertTrue(hasReason(r, "관심이 늘고"));
    }

    @Test
    public void 변동이_거의_없으면_기다려도_차이_없다고_알려준다() {
        TimingJudge.Result r = TimingJudge.judge(line(500_000, 503_000, 30), 501_000, null, null);
        assertTrue(hasReason(r, "변동이 거의 없어"));
        assertEquals("사도 괜찮아요".equals(r.grade) || "조금 지켜보세요".equals(r.grade), true);
    }

    /** 30일 내내 같은 가격이다가 오늘만 크게 바뀐 경우 (처음 만든 규칙의 빈틈을 테스트가 찾음) */
    @Test
    public void 평평한_기록에서_오늘_새_최저가면_최저가_점수를_받는다() {
        TimingJudge.Result r = TimingJudge.judge(line(1_000_000, 1_000_000, 30), 800_000, null, null);
        assertTrue(hasReason(r, "가장 싼 가격"));
        assertFalse(hasReason(r, "변동이 거의 없어"));
    }

    @Test
    public void 기록이_7일보다_적으면_참고용이다() {
        TimingJudge.Result r = TimingJudge.judge(new long[]{1_000_000, 980_000, 990_000}, 980_000, null, null);
        assertFalse(r.reliable);
    }

    @Test
    public void 점수는_0에서_100_사이다() {
        TimingJudge.Result cheap = TimingJudge.judge(line(2_000_000, 2_000_000, 30), 500_000, 60.0, 50.0);
        TimingJudge.Result pricey = TimingJudge.judge(line(500_000, 500_000, 30), 2_000_000, 0.0, -50.0);
        assertEquals(100, cheap.score);
        assertEquals(0, pricey.score);
    }

    @Test
    public void 기록이_없으면_판단하지_않는다() {
        assertNull(TimingJudge.judge(new long[0], 1_000_000, null, null));
        assertNull(TimingJudge.judge(null, 1_000_000, null, null));
    }

    @Test
    public void 등급_경계() {
        assertEquals("지금 사세요", TimingJudge.grade(75));
        assertEquals("사도 괜찮아요", TimingJudge.grade(55));
        assertEquals("조금 지켜보세요", TimingJudge.grade(40));
        assertEquals("기다리세요", TimingJudge.grade(39));
    }
}
