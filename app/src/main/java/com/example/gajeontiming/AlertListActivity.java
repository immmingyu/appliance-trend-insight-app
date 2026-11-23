package com.example.gajeontiming;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.gajeontiming.adapter.AlertAdapter;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.model.AlertModels.Alert;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-11 가격 알림 목록 화면 (로그인 필요).
 * 등록한 알림을 보여주고, 화면을 열 때마다 지금 최저가를 확인해 목표까지 얼마 남았는지 알려준다.
 * 알림을 삭제할 수 있다. 목표 가격 도달 확인 · 알림은 서버가 매일 하고 FCM 푸시로 보낸다.
 */
public class AlertListActivity extends AppCompatActivity {

    /** 등록 직후 서버 가격 확인(10~20초)이 끝났을 즈음 목록을 한 번 더 불러온다 */
    private static final long RECHECK_MS = 20_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean recheckDone;

    private RecyclerView list;
    private ProgressBar progress;
    private SwipeRefreshLayout swipe;
    private TextView stateText;
    private Button retryButton;
    private AlertAdapter adapter;

    /** 로그인하지 않은 채로 들어오면 로그인 화면을 거친다. 취소하면 이 화면도 닫는다. */
    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) loadAlerts();
                else finish();
            });

    /** 안드로이드 13 이상: 알림 권한 요청 */
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                // 거절하면 위쪽 안내가 남아 있으므로 따로 토스트는 띄우지 않는다
                updateNotifyBanner();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.alert_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        list = findViewById(R.id.alert_list);
        progress = findViewById(R.id.alert_progress);
        swipe = findViewById(R.id.alert_swipe);
        swipe.setOnRefreshListener(() -> {
            recheckDone = false;   // 당겨서 새로고침하면 확인 중인 알림을 20초 뒤 한 번 더 본다
            handler.removeCallbacksAndMessages(null);
            loadAlerts();
        });
        stateText = findViewById(R.id.alert_state);
        retryButton = findViewById(R.id.alert_retry);

        adapter = new AlertAdapter(new AlertAdapter.Listener() {
            @Override
            public void onOpen(Alert alert) {
                openLink(alert.productUrl);
            }

            @Override
            public void onDelete(Alert alert) {
                confirmDelete(alert);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        retryButton.setOnClickListener(v -> loadAlerts());

        askNotificationPermission();
        findViewById(R.id.alert_notify_settings).setOnClickListener(v -> openNotificationSettings());

        if (SessionManager.isLoggedIn()) {
            loadAlerts();
        } else {
            loginLauncher.launch(new Intent(this, LoginActivity.class));
        }
    }

    /** 설정에서 돌아왔을 때도 다시 확인한다 */
    @Override
    protected void onResume() {
        super.onResume();
        updateNotifyBanner();
    }

    /**
     * 알림이 꺼져 있으면 위쪽에 안내를 띄운다.
     * Android 13 이상은 권한을 두 번 거절하면 요청 창이 더 이상 안 떠서, 사용자는 알림이 왜 안 오는지 모르게 된다.
     * (권한 거절 · 설정에서 직접 끈 경우 모두 areNotificationsEnabled()가 false)
     */
    private void updateNotifyBanner() {
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        findViewById(R.id.alert_notify_banner).setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    /** 이 앱의 알림 설정 화면을 연다 (Android 8 미만은 앱 정보 화면) */
    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "설정 화면을 열 수 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ── 목록 불러오기 ─────────────────────────────────────────

    private void loadAlerts() {
        // 당겨서 새로고침 · 자동 재확인 중에는 가운데 로딩 대신 위쪽 동그라미만
        if (!swipe.isRefreshing() && adapter.getItemCount() == 0) progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        ApiClient.get().getAlerts().enqueue(new Callback<AlertModels.AlertListResponse>() {
            @Override
            public void onResponse(@NonNull Call<AlertModels.AlertListResponse> call,
                                   @NonNull Response<AlertModels.AlertListResponse> response) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                if (response.code() == 401) {                 // 로그인 만료
                    SessionManager.logout();
                    loginLauncher.launch(new Intent(AlertListActivity.this, LoginActivity.class));
                    return;
                }
                AlertModels.AlertListResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.alerts == null) {
                    showLoadError();
                    return;
                }
                adapter.setItems(body.alerts);
                if (body.alerts.isEmpty()) {
                    showState(getString(R.string.alert_empty), false);
                } else {
                    stateText.setVisibility(View.GONE);
                    loadCurrentPrices(body.alerts);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AlertModels.AlertListResponse> call, @NonNull Throwable t) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                showLoadError();
            }
        });
    }

    /** 처음 불러오기 실패는 가운데 안내 + 다시 시도, 이미 목록이 있으면(새로고침 실패) 목록은 두고 짧게 알린다 */
    private void showLoadError() {
        if (adapter.getItemCount() > 0) {
            Toast.makeText(this, R.string.alert_failed, Toast.LENGTH_SHORT).show();
        } else {
            showState(getString(R.string.alert_failed), true);
        }
    }

    /**
     * 알림마다 지금 최저가를 목록에 채운다. 서버가 확인한 가격(min_price)만 쓴다 (목표가 푸시와 같은 기준).
     * 서버는 등록 직후 뒤에서 한 번 (10~20초), 그 뒤로는 매일 새벽 가격을 확인한다.
     *   min_price 있음              → 지금 가격
     *   min_price · last_updated 없음 → 아직 확인 전: "가격 확인 중이에요" + 잠시 뒤 목록 다시 불러오기 (한 번만)
     *   last_updated만 있음          → 확인했지만 가격을 못 찾음: "지금 가격을 확인하지 못했어요"
     */
    private void loadCurrentPrices(List<Alert> alerts) {
        boolean checking = false;
        for (Alert a : alerts) {
            if (a.minPrice != null && a.minPrice > 0) {
                adapter.setCurrentPrice(a.alertId, Math.round(a.minPrice));
            } else if (a.minPrice == null && a.lastUpdated == null) {
                adapter.setCurrentPrice(a.alertId, AlertAdapter.CHECKING);
                checking = true;
            } else {
                adapter.setCurrentPrice(a.alertId, 0);
            }
        }
        if (checking && !recheckDone) {
            recheckDone = true;
            handler.postDelayed(this::loadAlerts, RECHECK_MS);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ── 삭제 ────────────────────────────────────────────────

    private void confirmDelete(Alert alert) {
        new AlertDialog.Builder(this)
                .setTitle("알림 삭제")
                .setMessage(alert.productName + "\n이 제품의 가격 알림을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> delete(alert))
                .show();
    }

    private void delete(Alert alert) {
        ApiClient.get().deleteAlert(alert.alertId).enqueue(new Callback<AuthModels.MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthModels.MessageResponse> call,
                                   @NonNull Response<AuthModels.MessageResponse> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(AlertListActivity.this,
                            ApiErrors.message(response, "삭제하지 못했어요"), Toast.LENGTH_SHORT).show();
                    return;
                }
                adapter.remove(alert);
                if (adapter.getItemCount() == 0) showState(getString(R.string.alert_empty), false);
            }

            @Override
            public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                Toast.makeText(AlertListActivity.this, "인터넷 연결을 확인해 주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── 공통 ───────────────────────────────────────────────

    private void showState(String text, boolean canRetry) {
        stateText.setText(text);
        stateText.setVisibility(View.VISIBLE);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    private void openLink(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }
}
