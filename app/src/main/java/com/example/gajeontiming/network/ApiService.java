package com.example.gajeontiming.network;

import com.example.gajeontiming.model.AiAnalysisResponse;
import com.example.gajeontiming.model.AiCompareResponse;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.model.AuthMe;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.model.MarketModels;
import com.example.gajeontiming.model.PriceMonitorResponse;
import com.example.gajeontiming.model.BestPriceResponse;
import com.example.gajeontiming.model.HealthResponse;
import com.example.gajeontiming.model.InsightModels;
import com.example.gajeontiming.model.PricePositionResponse;
import com.example.gajeontiming.model.ProductListResponse;
import com.example.gajeontiming.model.RecommendResponse;
import com.example.gajeontiming.model.ReportResponse;
import com.example.gajeontiming.model.TimingResponse;
import com.example.gajeontiming.model.TrendResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
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
    // category: 품목 이름(에어컨 · 로봇청소기 등). 제목에 품목 이름이 없을 때 서버가 이 값으로 후기 · 뉴스를 찾는다.
    // null이면 Retrofit이 파라미터를 빼고 보낸다
    Call<AiAnalysisResponse> getAiAnalysis(@Query("query") String query,
                                           @Query("category") String category);

    // F-05 제품 리포트: 관련 뉴스
    @GET("api/report")
    Call<ReportResponse> getReport(@Query("query") String query,
                                   @Query("category") String category);

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

    // 사업자 회원 가입 신청 → 관리자 승인 대기 (토큰 없음)
    @POST("api/auth/signup/b2b")
    Call<AuthModels.SignupB2bResponse> signupB2b(@Body AuthModels.SignupB2bRequest body);

    // 회원 탈퇴: 계정 · 가격 알림 등이 서버에서 지워지고, 기존 토큰은 이후 401이 된다
    @POST("api/auth/withdraw")
    Call<AuthModels.MessageResponse> withdraw(@Body AuthModels.WithdrawRequest body);

    // FCM 푸시 토큰 등록 (로그인 직후 · 토큰이 바뀔 때)
    @POST("api/user/push-token")
    Call<AuthModels.MessageResponse> registerPushToken(@Body AuthModels.PushTokenRequest body);

    // FCM 푸시 토큰 해제 (로그아웃 직전). 로그아웃과 동시에 보내도 되게 로그인 토큰을 직접 붙인다
    @POST("api/user/push-token/delete")
    Call<AuthModels.MessageResponse> unregisterPushToken(@Header("Authorization") String authorization,
                                                         @Body AuthModels.PushTokenRequest body);

    // B2B 가격 모니터 (승인된 사업자 회원만, 아니면 403)
    @GET("api/b2b/price-monitor")
    Call<PriceMonitorResponse> getPriceMonitor();

    // B2B 제품 시장 분석: 제품 검색 → 분석 (승인된 사업자 회원만, 분석은 10초~1분)
    @GET("api/b2b/product-search")
    Call<MarketModels.ProductSearch> searchMarketProducts(@Query("q") String query);

    @GET("api/b2b/search-analysis")
    Call<MarketModels.Analysis> getMarketAnalysis(@Query("q") String productName,
                                                  @Query("hint") String hint,
                                                  @Query("force") boolean force);

    // 비밀번호 변경 (로그인 필요, 이메일 회원만)
    @POST("api/auth/password/change")
    Call<AuthModels.MessageResponse> changePassword(@Body AuthModels.PasswordChangeRequest body);

    // 앱 소셜 로그인: SDK 토큰 → 기존 회원이면 로그인 토큰, 처음이면 가입 티켓
    @POST("api/auth/social/token")
    Call<AuthModels.SocialTokenResponse> socialToken(@Body AuthModels.SocialTokenRequest body);

    // 소셜 첫 가입 마무리 (가입 티켓 + 약관 동의 + 닉네임)
    @POST("api/auth/social/complete")
    Call<AuthModels.AuthResponse> socialComplete(@Body AuthModels.SocialCompleteRequest body);

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

    // F-15 내 정보 (로그인 필요): 회원 유형 · 상태 · 등급 · 소셜 연결 · 비밀번호 유무 · 가입일
    // 탈퇴 · 비활성화된 계정이면 401
    @GET("api/auth/me")
    Call<AuthMe> getAuthMe();

    // F-14 AI 추천: "에어컨 1인 자취 30만원 이하" 같은 문장 하나를 보낸다
    // (예산은 반드시 "숫자+만원" 형식이어야 서버가 알아듣는다)
    @GET("api/recommend")
    Call<RecommendResponse> recommend(@Query("query") String sentence);

    // F-17 AI 상담: 질문 + 직전 대화 4개 + 직전 조건 → 마크다운 답변 (로그인 불필요, 20~40초)
    @POST("api/insights/analyze")
    Call<InsightModels.Response> askInsight(@Body InsightModels.Request body);
}
