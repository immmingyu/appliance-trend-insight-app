package com.example.gajeontiming;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.example.gajeontiming.alert.PushRoute;
import com.example.gajeontiming.model.HealthResponse;
import com.example.gajeontiming.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-01 시작 화면.
 * 연한 보라 배경 + 로고 시작 화면(테마: Theme.GajeonTiming.Starting)을 1초 보여 준 뒤 홈으로 이동한다.
 * 그동안 잠든 서버를 깨우는 요청을 보내 두지만, 서버 응답은 기다리지 않는다.
 * (홈이 저장해 둔 트렌드를 먼저 보여 주므로 여기서 최대 1분씩 막을 필요가 없다.)
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1000;   // 시작 화면을 보여 주는 시간

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goHome = () -> {
        // 앱이 꺼져 있을 때 받은 가격 알림을 눌러서 들어왔으면 홈 위에 그 제품 화면을 연다
        Intent[] route = OnboardingActivity.isDone(this) ? PushRoute.fromExtras(this, getIntent().getExtras()) : null;
        if (route != null) {
            startActivities(route);
        } else {
            // 처음 실행이면 앱 소개부터, 이미 봤으면 바로 홈으로
            Class<?> next = OnboardingActivity.isDone(this) ? MainActivity.class : OnboardingActivity.class;
            startActivity(new Intent(this, next));
        }
        finish();   // 뒤로 가기를 눌러도 시작 화면으로 돌아오지 않게 닫는다
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // super.onCreate보다 먼저 호출해야 시작 화면 테마가 제대로 적용된다
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // 이 화면은 그릴 내용이 없으므로, 홈으로 넘어갈 때까지 시작 화면을 계속 띄워 둔다
        splash.setKeepOnScreenCondition(() -> true);

        wakeServer();
        handler.postDelayed(goHome, SPLASH_MS);
    }

    /** 1초 안에 뒤로 가기로 나가면 홈이 뒤늦게 열리지 않게 예약을 취소한다 */
    @Override
    protected void onDestroy() {
        handler.removeCallbacks(goHome);
        super.onDestroy();
    }

    /** 결과는 쓰지 않는다. 서버가 일찍 깨기 시작하게 하는 것이 목적 */
    private static void wakeServer() {
        ApiClient.get().health().enqueue(new Callback<HealthResponse>() {
            @Override
            public void onResponse(@NonNull Call<HealthResponse> call,
                                   @NonNull Response<HealthResponse> response) { }

            @Override
            public void onFailure(@NonNull Call<HealthResponse> call, @NonNull Throwable t) { }
        });
    }
}
