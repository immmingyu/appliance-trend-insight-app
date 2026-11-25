package com.example.gajeontiming.network;

/**
 * F-19 오프라인 캐시에 저장해도 되는 요청인지 정한다 (순수 Java, JUnit 테스트 가능).
 *
 * 저장하는 것: 누가 봐도 같은 공개 데이터를 읽는 GET 요청
 *   트렌드, 제품 검색, AI 분석 · 후기, 뉴스, 구매 타이밍, 품목 평균 비교, 품목 인식, 최저가, AI 추천
 * 저장하지 않는 것: 사람마다 다르거나 상태를 바꾸는 요청
 *   로그인 · 가입 · 탈퇴, 내 정보, 가격 알림, 푸시 토큰, 사업자(B2B) 데이터, AI 비교 · 상담, 서버 연결 확인
 */
public final class CachePolicy {

    private static final String[] PUBLIC_PATHS = {
            "/api/trend",
            "/api/naver/products",
            "/api/ai-analysis",
            "/api/report",
            "/api/timing",
            "/api/price-position",
            "/api/category",
            "/api/best-price",
            "/api/recommend"
    };

    private CachePolicy() { }

    /** method: "GET" 등, path: "/api/timing" 처럼 주소의 경로 부분 (? 뒤는 빼고) */
    public static boolean isCacheable(String method, String path) {
        if (!"GET".equals(method) || path == null) return false;
        for (String p : PUBLIC_PATHS) {
            if (path.equals(p)) return true;
        }
        return false;
    }
}
