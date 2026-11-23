package com.example.gajeontiming.b2b;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.gajeontiming.LoginActivity;
import com.example.gajeontiming.R;
import com.example.gajeontiming.model.PriceMonitorResponse;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.AuthGuard;
import com.example.gajeontiming.ui.Pulse;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * B2B 가격 모니터 (단계 3, 승인된 사업자 회원 전용). 비즈니스 메뉴의 첫 화면.
 * GET /api/b2b/price-monitor → 요약(하락 · 상승 · 전체 카테고리 수) + 카테고리별 평균가 · 변동률 · 흐름(하락 · 상승 · 보합).
 * 매입 적기 같은 판단은 보여 주지 않는다 (서버도 더 이상 판단하지 않음).
 * 401 → 로그인 후 다시, 403(승인 전 · 거절 · 개인 회원) · 402(등급) → 서버 문구만 보여 준다.
 */
public class PriceMonitorActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipe;
    private View summary, skeleton;
    private TextView stateText;
    private Button retryButton;
    private PriceMonitorAdapter adapter;
    private Call<PriceMonitorResponse> call;

    /** 로그인이 만료됐으면 로그인 화면을 거쳐 다시 불러온다. 취소하면 닫는다 */
    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) load();
                else finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_monitor);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.monitor_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        swipe = findViewById(R.id.monitor_swipe);
        summary = findViewById(R.id.monitor_summary);
        skeleton = findViewById(R.id.monitor_skeleton);
        stateText = findViewById(R.id.monitor_state);
        retryButton = findViewById(R.id.monitor_retry);

        RecyclerView list = findViewById(R.id.monitor_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PriceMonitorAdapter();
        list.setAdapter(adapter);

        swipe.setOnRefreshListener(this::load);
        retryButton.setOnClickListener(v -> load());
        load();
    }

    private void load() {
        if (call != null) call.cancel();
        boolean empty = adapter.getItemCount() == 0;
        setSkeleton(empty);                 // 처음이면 스켈레톤, 새로고침이면 당김 표시만
        swipe.setRefreshing(!empty);
        stateText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        call = ApiClient.get().getPriceMonitor();
        call.enqueue(new Callback<PriceMonitorResponse>() {
            @Override
            public void onResponse(@NonNull Call<PriceMonitorResponse> c,
                                   @NonNull Response<PriceMonitorResponse> response) {
                setSkeleton(false);
                swipe.setRefreshing(false);
                if (AuthGuard.isLoggedOut(response)) {
                    loginLauncher.launch(new Intent(PriceMonitorActivity.this, LoginActivity.class));
                    return;
                }
                PriceMonitorResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    // 403(승인 전 · 거절)은 다시 해도 같아서 다시 시도 버튼을 숨긴다
                    boolean forbidden = response.code() == 403 || response.code() == 402;
                    showState(AuthGuard.message(response, getString(R.string.monitor_failed)), !forbidden);
                    return;
                }
                bind(body);
            }

            @Override
            public void onFailure(@NonNull Call<PriceMonitorResponse> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                setSkeleton(false);
                swipe.setRefreshing(false);
                showState(getString(R.string.monitor_failed), true);
            }
        });
    }

    private void bind(PriceMonitorResponse body) {
        List<PriceMonitorResponse.Category> categories = body.categories;
        adapter.setItems(categories);
        if (categories == null || categories.isEmpty()) {
            summary.setVisibility(View.GONE);
            showState(getString(R.string.monitor_empty), true);
            return;
        }
        PriceMonitorResponse.Summary s = body.summary;
        if (s != null) {
            stat(R.id.monitor_stat_drop, s.dropCount, R.string.monitor_stat_drop);
            stat(R.id.monitor_stat_rise, s.riseCount, R.string.monitor_stat_rise);
            stat(R.id.monitor_stat_total, s.total, R.string.monitor_stat_total);
            TextView updated = findViewById(R.id.monitor_updated);
            String date = body.updatedAt != null ? body.updatedAt.replace('-', '.') : "";
            updated.setText(s.hasRealtime
                    ? getString(R.string.monitor_updated_realtime, date)
                    : getString(R.string.monitor_updated, date));
            summary.setVisibility(View.VISIBLE);
        }
    }

    private void stat(int includeId, int value, int label) {
        View cell = findViewById(includeId);
        ((TextView) cell.findViewById(R.id.stat_value)).setText(String.valueOf(value));
        ((TextView) cell.findViewById(R.id.stat_label)).setText(label);
    }

    private void setSkeleton(boolean show) {
        skeleton.setVisibility(show ? View.VISIBLE : View.GONE);
        ViewGroup group = (ViewGroup) skeleton;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) continue;
            if (show) Pulse.start(child);
            else Pulse.stop(child);
        }
    }

    private void showState(String text, boolean canRetry) {
        stateText.setText(text);
        stateText.setVisibility(View.VISIBLE);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (call != null) call.cancel();
        setSkeleton(false);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
