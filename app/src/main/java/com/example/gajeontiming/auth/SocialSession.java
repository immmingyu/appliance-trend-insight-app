package com.example.gajeontiming.auth;

import androidx.annotation.NonNull;

import com.example.gajeontiming.model.UserProfile;
import com.example.gajeontiming.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 소셜 로그인으로 받은 우리 서비스 토큰을 저장하고, 이메일 · 닉네임을 서버에서 받아 채운다.
 * (/social/token · /social/complete 응답에는 토큰만 있어서 /api/user/me로 한 번 더 묻는다)
 * 프로필을 못 받아도 로그인은 된 상태라 done은 항상 불린다.
 */
public final class SocialSession {

    private SocialSession() { }

    public static void start(String token, String provider, String fallbackName, Runnable done) {
        SessionManager.save(token, "", fallbackName == null ? "" : fallbackName, provider);
        ApiClient.get().getMe().enqueue(new Callback<UserProfile>() {
            @Override
            public void onResponse(@NonNull Call<UserProfile> call, @NonNull Response<UserProfile> response) {
                UserProfile me = response.body();
                if (response.isSuccessful() && me != null) {
                    String name = me.nickname != null && !me.nickname.isEmpty() ? me.nickname : me.companyName;
                    SessionManager.updateProfile(me.email == null ? "" : me.email,
                            name != null ? name : SessionManager.getName());
                }
                done.run();
            }

            @Override
            public void onFailure(@NonNull Call<UserProfile> call, @NonNull Throwable t) {
                done.run();
            }
        });
    }
}
