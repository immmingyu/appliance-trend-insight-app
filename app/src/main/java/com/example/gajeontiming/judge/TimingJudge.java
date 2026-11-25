package com.example.gajeontiming.judge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 확장 기능: 앱 자체 구매 판단 ("가전무쌍 점수").
 * 서버가 준 30일 가격 이력을 바탕으로 앱이 직접 0~100점과 근거를 계산한다 (순수 Java, JUnit 테스트 가능).
 *
 * 서버 판단은 "30일 평균보다 몇 % 싼가" 하나로 4단계를 정하지만,
 * 이 판단은 여러 요인을 점수로 더하고, 각 요인이 몇 점을 줬는지 근거로 함께 보여준다.
 *
 *   시작 50점
 *   ① 30일 평균 대비      평균보다 1% 쌀 때마다 +2점 (최대 ±30)
 *   ② 30일 범위 안 위치   최저가 +15 · 아래 20% +10 · 위 20% -10 · 30일 최고가보다 비쌈 -10
 *   ③ 최근 흐름          최근 7일 평균이 그 전 7일보다 3% 넘게 하락 -5 (더 내려갈 수 있음) / 상승 +5 (오르기 전에)
 *   ④ 검색 관심도         평소보다 +20% 넘게 +5 (수요가 몰리기 전에) / -20% 넘게 -5 (할인 가능성)
 *   ⑤ 출시가 대비 할인    30% 이상 +10 · 15% 이상 +5 · 5% 미만 -5 (아직 할인 전)
 *   ⑥ 30일 변동 폭이 2% 미만이면 점수 없이 "기다려도 큰 차이 없음" 근거만 붙인다
 *   → 0~100으로 자르고 75 이상 지금 사세요 · 55 이상 사도 괜찮아요 · 40 이상 조금 지켜보세요 · 그 밑 기다리세요
 *   가격 기록이 7일보다 적으면 참고용(reliable=false)
 */
public final class TimingJudge {

    public static final int MIN_RELIABLE_DAYS = 7;

    /** 판단 근거 한 줄: 설명과 그 요인이 준 점수 (0이면 점수 없는 참고 설명) */
    public static final class Reason {
        public final String text;
        public final int points;

        Reason(String text, int points) {
            this.text = text;
            this.points = points;
        }
    }

    public static final class Result {
        public final int score;            // 0 ~ 100
        public final String grade;         // 지금 사세요 | 사도 괜찮아요 | 조금 지켜보세요 | 기다리세요
        public final List<Reason> reasons; // 점수에 영향이 큰 순서
        public final boolean reliable;     // 가격 기록이 충분한지

        Result(int score, String grade, List<Reason> reasons, boolean reliable) {
            this.score = score;
            this.grade = grade;
            this.reasons = reasons;
            this.reliable = reliable;
        }
    }

    private TimingJudge() { }

    /**
     * @param prices         날짜순(오래된 → 최근) 하루 최저가. 0 이하 값은 무시
     * @param current        지금 가격
     * @param launchDiscPct  출시가 대비 할인율(%) — 모르면 null
     * @param searchDiffPct  최근 7일 검색 관심도가 평소보다 몇 % 높은지 — 모르면 null
     * @return 가격 기록이 없으면 null
     */
    public static Result judge(long[] prices, long current, Double launchDiscPct, Double searchDiffPct) {
        List<Long> valid = new ArrayList<>();
        if (prices != null) {
            for (long p : prices) if (p > 0) valid.add(p);
        }
        if (valid.isEmpty() || current <= 0) return null;

        long min = Collections.min(valid);
        long max = Collections.max(valid);
        double avg = average(valid, 0, valid.size());

        List<Reason> reasons = new ArrayList<>();
        int score = 50;

        // ① 30일 평균 대비
        double diffPct = (current - avg) / avg * 100.0;
        int p1 = clamp((int) Math.round(-diffPct * 2), -30, 30);
        if (Math.abs(diffPct) >= 0.5) {
            reasons.add(new Reason(String.format(Locale.KOREA, "30일 평균보다 %.1f%% %s",
                    Math.abs(diffPct), diffPct < 0 ? "저렴해요" : "비싸요"), p1));
        }
        score += p1;

        // ② 30일 범위 안 위치 · ⑥ 변동 폭
        //    새 최저가 · 기록보다 비싼 가격을 먼저 본다. 30일 내내 같은 가격이다가 오늘만 크게 바뀐 경우,
        //    변동 폭만 보면 "변동 없음"으로 잘못 빠지기 때문 (단위 테스트로 찾은 빈틈)
        double rangePct = (max - min) / avg * 100.0;
        int p2 = 0;
        if (current < min || (current == min && rangePct >= 2.0)) {
            p2 = 15;
            reasons.add(new Reason("지금이 30일 중 가장 싼 가격이에요", p2));
        } else if (current > max) {
            p2 = -10;
            reasons.add(new Reason("30일 동안 가장 비쌌던 가격보다도 높아요", p2));
        } else if (rangePct < 2.0) {
            reasons.add(new Reason("30일 동안 가격 변동이 거의 없어 기다려도 큰 차이가 없어요", 0));
        } else {
            double pos = (double) (current - min) / (max - min);   // 0 = 최저, 1 = 최고
            if (pos <= 0.2) {
                p2 = 10;
                reasons.add(new Reason("30일 가격 범위의 아래쪽이에요", p2));
            } else if (pos >= 0.8) {
                p2 = -10;
                reasons.add(new Reason("30일 가격 범위의 위쪽이에요", p2));
            }
        }
        score += p2;

        // ③ 최근 흐름 (최근 7일 평균 vs 그 전 7일 평균)
        if (valid.size() >= 10) {
            int n = valid.size();
            int recentFrom = Math.max(0, n - 7);
            int prevFrom = Math.max(0, recentFrom - 7);
            double recent = average(valid, recentFrom, n);
            double prev = average(valid, prevFrom, recentFrom);
            double trendPct = (recent - prev) / prev * 100.0;
            if (trendPct <= -3) {
                score -= 5;
                reasons.add(new Reason(String.format(Locale.KOREA,
                        "최근 일주일 동안 %.1f%% 내려가는 중이라 조금 더 내려갈 수 있어요", -trendPct), -5));
            } else if (trendPct >= 3) {
                score += 5;
                reasons.add(new Reason(String.format(Locale.KOREA,
                        "최근 일주일 동안 %.1f%% 오르는 중이라 더 오르기 전이 나아요", trendPct), 5));
            }
        }

        // ④ 검색 관심도
        if (searchDiffPct != null) {
            if (searchDiffPct >= 20) {
                score += 5;
                reasons.add(new Reason("검색 관심이 늘고 있어 수요가 몰리면 가격이 오를 수 있어요", 5));
            } else if (searchDiffPct <= -20) {
                score -= 5;
                reasons.add(new Reason("검색 관심이 줄고 있어 할인이 나올 가능성이 있어요", -5));
            }
        }

        // ⑤ 출시가 대비 할인
        if (launchDiscPct != null) {
            int p5 = 0;
            if (launchDiscPct >= 30) p5 = 10;
            else if (launchDiscPct >= 15) p5 = 5;
            else if (launchDiscPct < 5) p5 = -5;
            if (p5 != 0) {
                reasons.add(new Reason(String.format(Locale.KOREA, "출시가보다 %.0f%% %s",
                        Math.max(0, launchDiscPct), p5 < 0 ? "할인된 정도라 아직 할인 전이에요" : "할인됐어요"), p5));
            }
            score += p5;
        }

        score = clamp(score, 0, 100);
        reasons.sort((a, b) -> Math.abs(b.points) - Math.abs(a.points));
        return new Result(score, grade(score), reasons, valid.size() >= MIN_RELIABLE_DAYS);
    }

    static String grade(int score) {
        if (score >= 75) return "지금 사세요";
        if (score >= 55) return "사도 괜찮아요";
        if (score >= 40) return "조금 지켜보세요";
        return "기다리세요";
    }

    private static double average(List<Long> list, int from, int to) {
        if (to <= from) return list.get(0);
        double sum = 0;
        for (int i = from; i < to; i++) sum += list.get(i);
        return sum / (to - from);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
