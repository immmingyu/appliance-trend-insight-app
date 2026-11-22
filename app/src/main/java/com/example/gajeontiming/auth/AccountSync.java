package com.example.gajeontiming.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gajeontiming.model.AuthMe;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.AuthGuard;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 저장해 둔 로그인 상태를 서버(GET /api/auth/me)와 맞춘다.
 * 로그인이 유효한지(401이면 로그아웃) · 회원 유형 · 상태 · 등급 · 비밀번호 유무를 저장한다.
 * 앱 시작(MainActivity) · 소셜 로그인 직후에 부른다. 화면은 이 응답을 기다리지 않는다.
 */
public final class AccountSync {

    private AccountSync() { }

    /** done은 끝나면(성공 · 실패 · 로그아웃 모두) 불린다. 필요 없으면 null */
    public static void refresh(@Nullable Runnable done) {
        if (!SessionManager.isLoggedIn()) {
            if (done != null) done.run();
            return;
        }
        ApiClient.get().getAuthMe().enqueue(new Callback<AuthMe>() {
            @Override
            public void onResponse(@NonNull Call<AuthMe> call, @NonNull Response<AuthMe> response) {
                if (!AuthGuard.isLoggedOut(response)) {     // 401이면 로그아웃까지 처리됨
                    AuthMe me = response.body();
                    if (response.isSuccessful() && me != null) apply(me);
                }
                if (done != null) done.run();
            }

            @Override
            public void onFailure(@NonNull Call<AuthMe> call, @NonNull Throwable t) {
                // 인터넷이 안 되면 저장해 둔 상태로 그대로 쓴다
                if (done != null) done.run();
            }
        });
    }

    /** 응답을 저장한다 (마이페이지처럼 직접 /api/auth/me를 부른 화면도 이걸 쓴다) */
    public static void apply(AuthMe me) {
        SessionManager.saveAccount(me.userType, me.status, me.tier, me.tierExpiresAt);
        if (me.hasPassword != null) SessionManager.saveHasPassword(me.hasPassword);
        String name = me.displayName();
        SessionManager.updateProfile(me.email == null ? SessionManager.getEmail() : me.email,
                name != null && !name.isEmpty() ? name : SessionManager.getName());
    }
}
