package com.example.gajeontiming;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.adapter.AlertAdapter;
import com.example.gajeontiming.alert.PriceCheckWorker;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.model.AlertModels.Alert;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.model.BestPriceResponse;
import com.example.gajeontiming.model.Categories;
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
 * 알림 삭제, "지금 확인"(F-12 작업을 바로 실행)을 할 수 있다.
 */
public class AlertListActivity extends AppCompatActivity {

    private RecyclerView list;
    private ProgressBar progress;
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
                if (!granted) {
                    Toast.makeText(this, "알림을 꺼두면 가격이 내려가도 알려드릴 수 없어요",
                            Toast.LENGTH_LONG).show();
                }
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

        if (SessionManager.isLoggedIn()) {
            loadAlerts();
        } else {
            loginLauncher.launch(new Intent(this, LoginActivity.class));
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
        progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        ApiClient.get().getAlerts().enqueue(new Callback<AlertModels.AlertListResponse>() {
            @Override
            public void onResponse(@NonNull Call<AlertModels.AlertListResponse> call,
                                   @NonNull Response<AlertModels.AlertListResponse> response) {
                progress.setVisibility(View.GONE);
                if (response.code() == 401) {                 // 로그인 만료
                    SessionManager.logout();
                    loginLauncher.launch(new Intent(AlertListActivity.this, LoginActivity.class));
                    return;
                }
                AlertModels.AlertListResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.alerts == null) {
                    showState(getString(R.string.alert_failed), true);
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
                showState(getString(R.string.alert_failed), true);
            }
        });
    }

    /** 알림마다 지금 최저가를 확인해 목록에 채운다 (앱을 열 때 하는 확인) */
    private void loadCurrentPrices(List<Alert> alerts) {
        for (Alert a : alerts) {
            ApiClient.get().getBestPrice(a.productName, Categories.detect(a.productName))
                    .enqueue(new Callback<BestPriceResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<BestPriceResponse> call,
                                               @NonNull Response<BestPriceResponse> response) {
                            BestPriceResponse b = response.body();
                            long price = (b != null && b.available) ? b.price : 0;
                            // 등록 당시 가격과 2배 넘게 다르면 다른 상품으로 본다
                            if (price > 0 && a.currentPrice > 0) {
                                double ratio = price / a.currentPrice;
                                if (ratio < 0.5 || ratio > 2.0) price = 0;
                            }
                            adapter.setCurrentPrice(a.alertId, price);
                        }

                        @Override
                        public void onFailure(@NonNull Call<BestPriceResponse> call, @NonNull Throwable t) {
                            adapter.setCurrentPrice(a.alertId, 0);
                        }
                    });
        }
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
                PriceCheckWorker.forget(AlertListActivity.this, alert.alertId);
                if (adapter.getItemCount() == 0) showState(getString(R.string.alert_empty), false);
            }

            @Override
            public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                Toast.makeText(AlertListActivity.this, "인터넷 연결을 확인해 주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── 메뉴: 지금 확인 ───────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.alert_check_now)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == 1) {
            // 하루 1회 확인 작업을 지금 한 번 실행 → 목표 도달 제품이 있으면 휴대폰 알림이 뜬다
            PriceCheckWorker.runNow(this);
            loadAlerts();
            Toast.makeText(this, "가격을 확인하고 있어요. 목표에 도달한 제품이 있으면 알림이 와요",
                    Toast.LENGTH_LONG).show();
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
