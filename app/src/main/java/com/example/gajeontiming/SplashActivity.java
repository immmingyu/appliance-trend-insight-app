package com.example.gajeontiming;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gajeontiming.model.HealthResponse;
import com.example.gajeontiming.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-01 시작 화면.
 * 서버에 연결 확인 요청을 보내 잠든 서버를 깨우고, 응답이 오면 홈 화면으로 이동한다.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SLOW_NOTICE_MS = 5000;   // 5초가 넘으면 "깨우는 중" 안내로 바꿈

    private ProgressBar progress;
    private TextView message;
    private Button retryButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable showSlowNotice = () -> message.setText(R.string.splash_slow);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progress = findViewById(R.id.splash_progress);
        message = findViewById(R.id.splash_message);
        retryButton = findViewById(R.id.splash_retry);

        retryButton.setOnClickListener(v -> checkServer());
        checkServer();
    }

    private void checkServer() {
        progress.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        message.setText(R.string.splash_waking);
        handler.postDelayed(showSlowNotice, SLOW_NOTICE_MS);

        ApiClient.get().health().enqueue(new Callback<HealthResponse>() {
            @Override
            public void onResponse(@NonNull Call<HealthResponse> call,
                                   @NonNull Response<HealthResponse> response) {
                handler.removeCallbacks(showSlowNotice);
                if (response.isSuccessful()) {
                    goHome();
                } else {
                    showError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<HealthResponse> call, @NonNull Throwable t) {
                handler.removeCallbacks(showSlowNotice);
                showError();
            }
        });
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();   // 뒤로 가기를 눌러도 시작 화면으로 돌아오지 않게 닫는다
    }

    private void showError() {
        progress.setVisibility(View.GONE);
        message.setText(R.string.splash_failed);
        retryButton.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(showSlowNotice);
        super.onDestroy();
    }
}
