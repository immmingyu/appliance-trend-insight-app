package com.example.gajeontiming.auth;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gajeontiming.SplashActivity;
import com.example.gajeontiming.model.AuthMe;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.AuthGuard;
import com.example.gajeontiming.ui.B2bStatusDialog;
import com.example.gajeontiming.ui.ForegroundTracker;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 저장해 둔 로그인 상태를 서버(GET /api/auth/me)와 맞춘다.
 * 로그인이 유효한지(401이면 로그아웃) · 회원 유형 · 상태 · 등급 · 비밀번호 유무를 저장한다.
 * 앱 시작(MainActivity) · 소셜 로그인 직후에 부른다. 화면은 이 응답을 기다리지 않는다.
 */
public final class AccountSync {

    private static final long MIN_INTERVAL_MS = 60 * 1000;   // 화면을 오갈 때마다 부르지 않도록
    private static long lastRefreshAt;

    private AccountSync() { }

    /**
     * 마지막 확인 뒤 1분이 지났을 때만 refresh (홈 화면 onResume용).
     * 앱을 백그라운드에 둔 사이 관리자가 승인해도 돌아오면 바로 알 수 있게 한다.
     */
    public static void refreshIfStale(@Nullable Runnable done) {
        if (System.currentTimeMillis() - lastRefreshAt < MIN_INTERVAL_MS) return;
        refresh(done);
    }

    /** done은 끝나면(성공 · 실패 · 로그아웃 모두) 불린다. 필요 없으면 null */
    public static void refresh(@Nullable Runnable done) {
        if (!SessionManager.isLoggedIn()) {
            if (done != null) done.run();
            return;
        }
        lastRefreshAt = System.currentTimeMillis();
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

    private static final long PENDING_CHECK_MS = 10 * 1000;   // 승인 대기 중 화면 전환마다 묻지 않도록
    private static long lastPendingCheckAt;

    /**
     * 앱의 어떤 화면이든 다시 보일 때 (ForegroundTracker가 부름).
     * 백그라운드에서 승인 · 거절 푸시를 받고 돌아오면, 마지막으로 보던 화면이 홈이 아니어도 여기서 안내한다.
     *   1) 이미 알고 있는 결과가 있으면 바로 안내 창
     *   2) 승인 대기 · 거절 상태면 서버에 다시 물어보고(10초 간격), 바뀌었으면 안내 창 + 화면 갱신
     */
    public static void onScreenResumed() {
        showStatusNotice();
        // 승인 대기 · 거절 사업자만 (거절 후 다시 승인될 수도 있다)
        if (!SessionManager.isB2bPending() && !SessionManager.isB2bRejected()) return;
        long now = System.currentTimeMillis();
        if (now - lastPendingCheckAt < PENDING_CHECK_MS) return;
        lastPendingCheckAt = now;
        refresh(() -> {
            showStatusNotice();
            notifyChanged();
        });
    }

    /** 아직 안 띄운 사업자 승인 · 거절 결과가 있으면 지금 화면에 안내 창 (시작 화면은 건너뜀) */
    public static void showStatusNotice() {
        Activity screen = ForegroundTracker.current();
        if (screen == null || screen instanceof SplashActivity) return;
        String notice = SessionManager.consumeStatusNotice();
        if (notice != null) B2bStatusDialog.show(screen, "active".equals(notice));
    }

    /** 지금 떠 있는 화면이 회원 정보를 보여 주는 화면(홈 · 마이페이지)이면 다시 그리게 한다. 메인 스레드에서 */
    public static void notifyChanged() {
        Activity a = ForegroundTracker.current();
        if (a instanceof AccountAware) ((AccountAware) a).onAccountChanged();
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
