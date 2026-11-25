package com.example.gajeontiming.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** F-19 오프라인 캐시에 저장할 요청 판별 단위 테스트 */
public class CachePolicyTest {

    @Test
    public void 공개_데이터_GET은_저장한다() {
        assertTrue(CachePolicy.isCacheable("GET", "/api/trend"));
        assertTrue(CachePolicy.isCacheable("GET", "/api/naver/products"));
        assertTrue(CachePolicy.isCacheable("GET", "/api/ai-analysis"));
        assertTrue(CachePolicy.isCacheable("GET", "/api/timing"));
        assertTrue(CachePolicy.isCacheable("GET", "/api/best-price"));
    }

    @Test
    public void 사람마다_다르거나_상태를_바꾸는_요청은_저장하지_않는다() {
        assertFalse(CachePolicy.isCacheable("GET", "/api/user/alerts"));     // 내 가격 알림
        assertFalse(CachePolicy.isCacheable("GET", "/api/auth/me"));         // 내 정보
        assertFalse(CachePolicy.isCacheable("GET", "/api/b2b/price-monitor"));
        assertFalse(CachePolicy.isCacheable("GET", "/api/ai-compare"));
        assertFalse(CachePolicy.isCacheable("POST", "/api/auth/login"));
        assertFalse(CachePolicy.isCacheable("POST", "/api/insights/analyze"));
        assertFalse(CachePolicy.isCacheable("GET", "/"));                    // 서버 연결 확인은 항상 실제로
    }

    @Test
    public void 비슷한_경로라도_정확히_같아야_저장한다() {
        assertFalse(CachePolicy.isCacheable("GET", "/api/trend/admin"));
        assertFalse(CachePolicy.isCacheable("GET", null));
    }
}
