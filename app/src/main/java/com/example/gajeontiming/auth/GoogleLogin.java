package com.example.gajeontiming.auth;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.gajeontiming.BuildConfig;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * Google 로그인 (Credential Manager의 "Google로 로그인" 창).
 * 받은 ID 토큰은 서버 POST /api/auth/social/token 으로 보내고, 서버는 aud가 웹 클라이언트 ID인지 검증한다.
 * 그래서 serverClientId에는 Android 클라이언트가 아니라 "웹 클라이언트 ID"를 넣는다 (local.properties의 GOOGLE_WEB_CLIENT_ID).
 * 단, Google Cloud에 이 앱의 Android 클라이언트(패키지 이름 + SHA-1)도 등록돼 있어야 창이 뜬다.
 */
public final class GoogleLogin {

    private GoogleLogin() { }

    public static boolean isConfigured() {
        return !BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty();
    }

    /** onToken에는 Google ID 토큰이 온다 */
    public static void login(Activity activity, SocialLoginCallback callback) {
        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(activity).getCredentialAsync(activity, request, null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                callback.onToken(GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
                            } catch (Exception e) {
                                callback.onError("Google 로그인 정보를 읽지 못했어요. 다시 시도해 주세요.");
                            }
                        } else {
                            callback.onError("Google 로그인 정보를 읽지 못했어요. 다시 시도해 주세요.");
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancel();
                        } else if (e instanceof NoCredentialException) {
                            callback.onError("이 휴대폰에서 쓸 수 있는 Google 계정이 없어요.");
                        } else {
                            callback.onError("Google 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.");
                        }
                    }
                });
    }

    /** 우리 서비스 로그아웃 · 탈퇴 때 다음 로그인에서 계정을 자동으로 고르지 않게 정리한다 (실패해도 무시) */
    public static void logout(Context context) {
        if (!isConfigured()) return;
        CredentialManager.create(context).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void unused) { }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) { }
                });
    }
}
