package com.example.gajeontiming.auth;

import android.content.Context;

import com.example.gajeontiming.BuildConfig;
import com.kakao.sdk.common.KakaoSdk;
import com.kakao.sdk.common.model.ClientError;
import com.kakao.sdk.common.model.ClientErrorCause;
import com.kakao.sdk.user.UserApiClient;

import kotlin.Unit;

/**
 * 카카오 로그인 SDK를 Java에서 쓰기 쉽게 감싼다.
 * 카카오톡이 깔려 있으면 카카오톡으로, 없으면 카카오계정(브라우저)으로 로그인해 액세스 토큰을 받는다.
 * 받은 토큰은 서버 POST /api/auth/social/token 으로 보내 우리 서비스 로그인 토큰으로 바꾼다.
 *
 * 앱 키는 local.properties의 KAKAO_NATIVE_APP_KEY (BuildConfig로 들어옴). 비어 있으면 카카오 로그인을 숨긴다.
 */
public final class KakaoLogin {

    private KakaoLogin() { }

    public static boolean isConfigured() {
        return !BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty();
    }

    /** GajeonApp.onCreate에서 한 번 */
    public static void init(Context context) {
        if (isConfigured()) KakaoSdk.init(context, BuildConfig.KAKAO_NATIVE_APP_KEY);
    }

    public static void login(Context context, SocialLoginCallback callback) {
        UserApiClient api = UserApiClient.getInstance();
        if (api.isKakaoTalkLoginAvailable(context)) {
            api.loginWithKakaoTalk(context, (token, error) -> {
                if (token != null) {
                    callback.onToken(token.getAccessToken());
                } else if (isCancelled(error)) {
                    // 카카오톡 로그인 화면에서 사용자가 취소했으면 계정 로그인으로 넘기지 않는다 (카카오 권장)
                    callback.onCancel();
                } else {
                    // 카카오톡에 연결된 계정이 없는 등 → 카카오계정(브라우저) 로그인으로 다시 시도
                    loginWithAccount(context, callback);
                }
                return Unit.INSTANCE;
            });
        } else {
            loginWithAccount(context, callback);
        }
    }

    private static void loginWithAccount(Context context, SocialLoginCallback callback) {
        UserApiClient.getInstance().loginWithKakaoAccount(context, (token, error) -> {
            if (token != null) callback.onToken(token.getAccessToken());
            else if (isCancelled(error)) callback.onCancel();
            else callback.onError("카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.");
            return Unit.INSTANCE;
        });
    }

    private static boolean isCancelled(Throwable error) {
        return error instanceof ClientError && ((ClientError) error).getReason() == ClientErrorCause.Cancelled;
    }

    /** 우리 서비스 로그아웃 · 탈퇴 때 이 기기의 카카오 토큰도 지운다 (실패해도 무시) */
    public static void logout() {
        if (!isConfigured()) return;
        UserApiClient.getInstance().logout(error -> Unit.INSTANCE);
    }
}
