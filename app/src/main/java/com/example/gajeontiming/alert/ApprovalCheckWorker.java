package com.example.gajeontiming.alert;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gajeontiming.BuildConfig;
import com.example.gajeontiming.auth.AccountSync;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AuthMe;
import com.example.gajeontiming.network.ApiClient;

import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * 사업자 승인 확인 (B2B 단계 1).
 * 승인 대기 중인 사업자 회원일 때만 6시간마다(디버그 빌드 15분) GET /api/auth/me로 상태를 확인하고,
 * 승인 · 거절되면 휴대폰 알림을 보낸 뒤 스스로 멈춘다. 앱을 열지 않아도 결과를 알 수 있게 하는 용도.
 * (앱을 열면 홈 화면도 같은 확인을 한다 → 거기서 본 경우엔 앱 안 안내 창으로 알려 준다)
 */
public class ApprovalCheckWorker extends Worker {

    private static final String UNIQUE = "b2b_approval_check";
    // 승인 · 거절은 서버 푸시(PushService)로 바로 온다. 이건 푸시가 안 왔을 때를 위한 안전장치라 길게:
    // 출시 빌드 6시간 · 디버그 빌드 15분(안드로이드가 허용하는 최소 주기, 테스트용)
    private static final long INTERVAL_MINUTES = BuildConfig.DEBUG ? 15 : 6 * 60;

    public ApprovalCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 승인 대기 중이면 확인 작업을 예약하고, 아니면 멈춘다. 로그인 · 가입 신청 · 회원 정보 갱신 뒤에 부른다 */
    public static void sync(Context context) {
        WorkManager wm = WorkManager.getInstance(context);
        if (!SessionManager.isB2bPending()) {
            wm.cancelUniqueWork(UNIQUE);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ApprovalCheckWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)   // 방금 확인했으니 다음 주기부터
                .build();
        wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!SessionManager.isB2bPending()) {              // 로그아웃 · 이미 결과가 나옴
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE);
            return Result.success();
        }
        try {
            // Worker는 이미 백그라운드 스레드라서 enqueue 대신 execute(동기 호출)를 쓴다
            Response<AuthMe> res = ApiClient.get().getAuthMe().execute();
            if (res.code() == 401) {                       // 탈퇴 · 만료
                SessionManager.logout();
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE);
                return Result.success();
            }
            AuthMe me = res.body();
            if (!res.isSuccessful() || me == null) return Result.success();   // 다음 주기에 다시
            AccountSync.apply(me);

            if (SessionManager.isB2b()) {
                SessionManager.consumeApprovedNotice();    // 휴대폰 알림으로 알렸으니 앱 안 안내 창은 생략
                AlertNotifier.notifyB2bResult(context, true);
            } else if (SessionManager.isB2bRejected()) {
                AlertNotifier.notifyB2bResult(context, false);
            }
            if (!SessionManager.isB2bPending()) WorkManager.getInstance(context).cancelUniqueWork(UNIQUE);
        } catch (Exception e) {
            // 인터넷 문제 등: 다음 주기에 다시
        }
        return Result.success();
    }
}
