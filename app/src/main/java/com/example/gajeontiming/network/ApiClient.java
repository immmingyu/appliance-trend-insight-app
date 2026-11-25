package com.example.gajeontiming.network;

import android.content.Context;

import com.example.gajeontiming.auth.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 서버와 통신하는 Retrofit 객체를 앱 전체에서 하나만 만들어 쓴다.
 * 무료 서버는 잠들어 있다가 첫 요청에 최대 1분 정도 걸려 깨어나므로 대기 시간을 넉넉히 잡는다.
 * 로그인 상태면 모든 요청에 "Authorization: Bearer 토큰" 헤더를 자동으로 붙인다.
 * F-19: 공개 데이터는 기기에 저장해 두고, 연결이 안 되면 저장본을 대신 돌려준다 (OfflineCache).
 */
public class ApiClient {

    // 서버 주소. 끝의 '/'를 지우지 말 것 (Retrofit 규칙)
    public static final String BASE_URL = "https://appliance-trend-insight.onrender.com/";

    private static ApiService service;
    private static Context appContext;

    /** 앱 시작 시 한 번 호출 (GajeonApp.onCreate). 오프라인 캐시 폴더를 쓰기 위해 필요 */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static ApiService get() {
        if (service == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String token = SessionManager.getToken();
                        if (token != null) {
                            request = request.newBuilder()
                                    .header("Authorization", "Bearer " + token)
                                    .build();
                        }
                        return chain.proceed(request);
                    });
            if (appContext != null) {                                   // F-19 오프라인 캐시
                builder.cache(OfflineCache.create(appContext))
                        .addInterceptor(OfflineCache.fallbackInterceptor())
                        .addNetworkInterceptor(OfflineCache.storeInterceptor());
            }
            OkHttpClient client = builder.build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(ApiService.class);
        }
        return service;
    }
}
