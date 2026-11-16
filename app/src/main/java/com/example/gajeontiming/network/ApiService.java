package com.example.gajeontiming.network;

import com.example.gajeontiming.model.AiAnalysisResponse;
import com.example.gajeontiming.model.AiCompareResponse;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.model.BestPriceResponse;
import com.example.gajeontiming.model.HealthResponse;
import com.example.gajeontiming.model.PricePositionResponse;
import com.example.gajeontiming.model.ProductListResponse;
import com.example.gajeontiming.model.ReportResponse;
import com.example.gajeontiming.model.TimingResponse;
import com.example.gajeontiming.model.UserProfile;
import com.example.gajeontiming.model.TrendResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** 서버 API 목록. 기능이 늘어날 때마다 여기에 한 줄씩 추가한다. */
public interface ApiService {

    // F-01 서버 연결 확인: {"status": "ok"}
    @GET("/")
    Call<HealthResponse> health();

    // F-02 트렌드 TOP10 (category를 null로 주면 전체)
    @GET("api/trend")
    Call<TrendResponse> getTrend(@Query("category") String category);

    // F-04 제품 검색. sort: sim(정확도) | asc(낮은 가격) | dsc(높은 가격)
    // category를 주면 서버가 액세서리 · 부품 같은 엉뚱한 상품을 걸러준다 (null이면 생략)
    @GET("api/naver/products")
    Call<ProductListResponse> searchProducts(
            @Query("query") String query,
            @Query("page") int page,
            @Query("display") int display,
            @Query("sort") String sort,
            @Query("category") String category);

    // F-05 제품 리포트: AI 분석 + 실사용 후기 (AI 호출이 있어 10~20초 걸릴 수 있음)
    @GET("api/ai-analysis")
    Call<AiAnalysisResponse> getAiAnalysis(@Query("query") String query);

    // F-05 제품 리포트: 관련 뉴스
    @GET("api/report")
    Call<ReportResponse> getReport(@Query("query") String query);

    // F-06 구매 타이밍: 30일 가격 이력 + 구매 판단
    // category에는 제품명을, product_id에는 제품 번호를 넣는다 (서버가 그 제품을 찾아 추적)
    // price를 주면 서버가 가격이 크게 다른 상품(소모품 등)을 후보에서 뺀다
    @GET("api/timing")
    Call<TimingResponse> getTiming(@Query("category") String productTitle,
                                   @Query("product_id") String productId,
                                   @Query("price") long price);

    // F-06 같은 품목 평균과 비교 (category는 "에어컨" 같은 품목 이름)
    @GET("api/price-position")
    Call<PricePositionResponse> getPricePosition(@Query("category") String category,
                                                 @Query("price") long price);

    // F-07 제품 비교: 지금 가장 싼 판매처 가격
    // category를 같이 보내야 서버가 소모품 · 부품을 빼고 찾는다 (null이면 생략)
    @GET("api/best-price")
    Call<BestPriceResponse> getBestPrice(@Query("query") String query,
                                         @Query("category") String category);

    // ── F-09 회원가입 · F-10 로그인 ───────────────────────────

    // 1) 이메일로 6자리 인증번호 보내기 (이미 가입된 이메일이면 409)
    @POST("api/auth/send-code")
    Call<AuthModels.MessageResponse> sendCode(@Body AuthModels.EmailRequest body);

    // 2) 인증번호 확인 (10분 안에)
    @POST("api/auth/verify-code")
    Call<AuthModels.MessageResponse> verifyCode(@Body AuthModels.VerifyRequest body);

    // 3) 일반 회원 가입 → 바로 토큰을 받아 로그인 상태가 된다
    @POST("api/auth/signup/b2c")
    Call<AuthModels.AuthResponse> signup(@Body AuthModels.SignupRequest body);

    // 로그인 (틀리면 401)
    @POST("api/auth/login")
    Call<AuthModels.AuthResponse> login(@Body AuthModels.LoginRequest body);

    // F-13 AI 비교 (로그인 필요 → ApiClient가 토큰을 자동으로 붙임, 없거나 만료면 401)
    // 두 제품의 후기와 뉴스를 모아 AI가 비교하므로 20초 정도 걸릴 수 있다
    @GET("api/ai-compare")
    Call<AiCompareResponse> aiCompare(@Query("q1") String productA,
                                      @Query("q2") String productB);

    // ── F-11 가격 알림 관리 (로그인 필요) ─────────────────────

    @GET("api/user/alerts")
    Call<AlertModels.AlertListResponse> getAlerts();

    // 같은 제품을 이미 등록했으면 409
    @POST("api/user/alerts")
    Call<AlertModels.CreateResponse> createAlert(@Body AlertModels.CreateRequest body);

    @DELETE("api/user/alerts/{id}")
    Call<AuthModels.MessageResponse> deleteAlert(@Path("id") long alertId);

    // F-15 마이페이지: 내 정보 (로그인 필요)
    @GET("api/user/me")
    Call<UserProfile> getMe();
}
