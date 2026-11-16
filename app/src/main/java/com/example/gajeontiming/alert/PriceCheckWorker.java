package com.example.gajeontiming.alert;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.model.BestPriceResponse;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.network.ApiClient;

import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * F-12 가격 알림 확인 작업. 앱이 꺼져 있어도 WorkManager가 하루에 한 번 실행한다.
 * 등록한 알림마다 지금 최저가를 확인해서 목표 가격 이하면 휴대폰 알림을 띄운다.
 *
 * 서버에도 알림 확인 기능이 있지만 품목 평균가 기준(웹의 사업자용)이라,
 * 개별 제품 가격 확인은 앱에서 한다.
 */
public class PriceCheckWorker extends Worker {

    private static final String UNIQUE_DAILY = "daily_price_check";
    private static final String PREF = "alert_notified";

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 하루 1회 확인 예약 (이미 예약돼 있으면 그대로 둔다). 인터넷이 될 때만 실행된다. */
    public static void scheduleDaily(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                PriceCheckWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_DAILY, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** "지금 확인" 버튼용: 한 번만 바로 실행 */
    public static void runNow(Context context) {
        WorkManager.getInstance(context)
                .enqueue(new OneTimeWorkRequest.Builder(PriceCheckWorker.class).build());
    }

    /** 알림이 삭제되면 "이미 알림 보낸 가격" 기록도 지운다 */
    public static void forget(Context context, long alertId) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().remove(String.valueOf(alertId)).apply();
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!SessionManager.isLoggedIn()) return Result.success();   // 로그아웃 상태면 할 일 없음

        try {
            // Worker는 이미 백그라운드 스레드라서 enqueue 대신 execute(동기 호출)를 쓴다
            Response<AlertModels.AlertListResponse> res = ApiClient.get().getAlerts().execute();
            if (res.code() == 401) return Result.success();            // 로그인 만료: 다음에 다시
            if (!res.isSuccessful() || res.body() == null || res.body().alerts == null) {
                return Result.retry();
            }

            SharedPreferences notified = getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE);

            for (AlertModels.Alert a : res.body().alerts) {
                long price = currentPrice(a);
                if (price <= 0 || price > (long) a.targetPrice) continue;

                // 같은 가격으로 매일 알림이 오지 않게, 지난번 알림보다 더 내려갔을 때만 다시 알린다
                String key = String.valueOf(a.alertId);
                long lastNotified = notified.getLong(key, Long.MAX_VALUE);
                if (price >= lastNotified) continue;

                AlertNotifier.notifyPriceDrop(getApplicationContext(),
                        a.alertId, a.productName, price, (long) a.targetPrice);
                notified.edit().putLong(key, price).apply();
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();   // 네트워크 오류 등 → WorkManager가 잠시 뒤 다시 시도
        }
    }

    /**
     * 알림 제품의 지금 최저가. 등록 당시 가격과 2배 넘게 다르면
     * 이름만 비슷한 다른 상품(소모품 등)으로 보고 0을 돌려준다.
     */
    static long currentPrice(AlertModels.Alert a) throws Exception {
        Response<BestPriceResponse> r = ApiClient.get()
                .getBestPrice(a.productName, Categories.detect(a.productName)).execute();
        BestPriceResponse body = r.body();
        if (!r.isSuccessful() || body == null || !body.available || body.price <= 0) return 0;
        if (a.currentPrice > 0) {
            double ratio = body.price / a.currentPrice;
            if (ratio < 0.5 || ratio > 2.0) return 0;
        }
        return body.price;
    }
}
