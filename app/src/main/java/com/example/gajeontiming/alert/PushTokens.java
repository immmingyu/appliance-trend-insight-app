package com.example.gajeontiming.alert;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.gajeontiming.BuildConfig;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * FCM 푸시 토큰을 서버에 등록 · 해제한다.
 *   등록: 로그인 직후 · 앱 시작 · 토큰이 바뀔 때(PushService.onNewToken) → POST /api/user/push-token
 *   해제: 로그아웃 직전 → POST /api/user/push-token/delete (로그인 토큰이 필요해서 지우기 전에 보낸다)
 * app/google-services.json이 없어 Firebase가 초기화되지 않은 빌드에서는 아무것도 하지 않는다.
 */
public final class PushTokens {

    private static Context appContext;
    static final String TAG = "GajeonPush";   // 디버그 빌드 Logcat 필터용

    /** 디버그 빌드에서만 Logcat에 남긴다 (푸시가 어디서 끊기는지 확인용) */
    static void log(String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, message);
    }

    private PushTokens() { }

    /** GajeonApp.onCreate에서 한 번 */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /** Firebase 설정 파일이 들어간 빌드인지 */
    public static boolean isAvailable() {
        return appContext != null && !FirebaseApp.getApps(appContext).isEmpty();
    }

    /** 로그인 상태면 이 휴대폰의 토큰을 서버에 등록 (같은 토큰을 여러 번 보내도 된다) */
    public static void register() {
        if (!isAvailable()) {
            log("Firebase 없음 (google-services.json 없이 빌드됨) → 푸시 꺼짐");
            return;
        }
        if (!SessionManager.isLoggedIn()) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(PushTokens::send)
                .addOnFailureListener(e -> log("FCM 토큰 받기 실패: " + e));
    }

    /** 새 토큰을 받았을 때 (PushService) */
    public static void send(String token) {
        if (token == null || token.isEmpty() || !SessionManager.isLoggedIn()) return;
        log("토큰 등록 요청: " + token.substring(0, Math.min(12, token.length())) + "…");
        ApiClient.get().registerPushToken(new AuthModels.PushTokenRequest(token, "android"))
                .enqueue(logged("등록"));
    }

    /**
     * 로그아웃 직전: 서버에서 이 휴대폰 토큰을 지운다 (다른 사람이 로그인해도 앞 사람 알림이 오지 않게).
     * 지금 로그인 토큰을 붙여 보내므로, 이 호출 바로 뒤에 SessionManager.logout()을 해도 된다.
     */
    public static void unregister() {
        if (!isAvailable() || !SessionManager.isLoggedIn()) return;
        String auth = "Bearer " + SessionManager.getToken();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                ApiClient.get().unregisterPushToken(auth, new AuthModels.PushTokenRequest(token, null))
                        .enqueue(logged("해제")));
    }

    /** 결과는 화면에 쓰지 않고 디버그 로그만 남긴다 (실패하면 다음 등록 때 다시) */
    private static Callback<AuthModels.MessageResponse> logged(String what) {
        return new Callback<AuthModels.MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthModels.MessageResponse> call,
                                   @NonNull Response<AuthModels.MessageResponse> response) {
                log("토큰 " + what + " 응답: HTTP " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                log("토큰 " + what + " 실패: " + t);
            }
        };
    }
}
