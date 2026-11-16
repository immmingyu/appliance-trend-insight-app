package com.example.gajeontiming;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.os.Bundle;
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

import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.PricePositionResponse;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.model.TimingResponse;
import com.example.gajeontiming.model.TimingResponse.Analysis;
import com.example.gajeontiming.model.TimingResponse.PricePoint;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-06 구매 타이밍 화면.
 * 제품 리포트에서 Product와 품목 이름을 받아, 30일 가격 그래프와 "지금 사도 되는지" 판단을 보여준다.
 */
public class TimingActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "product";
    public static final String EXTRA_CATEGORY = "category";

    private Product product;
    private String category;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    private ProgressBar progress;
    private TextView stateText;
    private Button retryButton;
    private View content;
    private View stateBox;     // 로딩 · 오류 표시 묶음
    private LineChart chart;

    private Analysis lastAnalysis;   // 알림 등록할 때 현재가 · 최저가를 쓰기 위해 보관

    /** 로그인하지 않은 채로 알림을 누르면 로그인을 거쳐 바로 등록 창을 띄운다 */
    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) showAlertDialog();
            });

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });

    private Call<TimingResponse> timingCall;
    private Call<PricePositionResponse> positionCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timing);

        product = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (product == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.timing_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        progress = findViewById(R.id.timing_progress);
        stateText = findViewById(R.id.timing_state);
        retryButton = findViewById(R.id.timing_retry);
        content = findViewById(R.id.timing_content);
        stateBox = findViewById(R.id.timing_state_box);
        chart = findViewById(R.id.timing_chart);

        ((TextView) findViewById(R.id.timing_product)).setText(product.title);

        retryButton.setOnClickListener(v -> loadTiming());
        findViewById(R.id.timing_alert_button).setOnClickListener(v -> onAlertClick());

        loadTiming();
    }

    // ── 서버 호출 ─────────────────────────────────────────────

    private void loadTiming() {
        stateBox.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.timing_loading);
        retryButton.setVisibility(View.GONE);
        content.setVisibility(View.GONE);

        timingCall = ApiClient.get().getTiming(buildTimingQuery(), product.id, product.price);
        timingCall.enqueue(new Callback<TimingResponse>() {
            @Override
            public void onResponse(@NonNull Call<TimingResponse> call,
                                   @NonNull Response<TimingResponse> response) {
                TimingResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    showError(getString(R.string.timing_failed));
                } else if (body.analysis == null) {
                    // 서버가 가격을 충분히 모으지 못한 경우 (error에 이유가 들어 있음)
                    // 서버가 사용자에게 보여줄 이유("가격 데이터가 부족…", "같은 제품을 찾지 못…")를 주면 그대로 쓴다
                    boolean friendly = body.error != null
                            && (body.error.startsWith("가격") || body.error.startsWith("같은 제품"));
                    showError(friendly ? body.error : getString(R.string.timing_no_data));
                } else if (isDifferentProduct(body)) {
                    // 서버가 이름만 비슷한 다른 상품(소모품 등)을 잡았으면 틀린 판단을 보여주지 않는다
                    showError(getString(R.string.timing_mismatch));
                } else {
                    bind(body);
                    loadPricePosition(body.analysis.curMin);
                }
            }

            @Override
            public void onFailure(@NonNull Call<TimingResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                showError(getString(R.string.timing_failed));
            }
        });
    }

    /**
     * 서버는 이 검색어로 다시 검색해서 추적할 제품을 고른다.
     * 제품명에 품목 이름(예: "로봇청소기")이 있어야 서버가 소모품 · 저가 상품을 걸러내므로,
     * 품목을 알고 있는데 제품명에 없으면 뒤에 붙여 준다.
     */
    private String buildTimingQuery() {
        if (category != null && !product.title.contains(category)) {
            return product.title + " " + category;
        }
        return product.title;
    }

    /**
     * 서버가 추적한 제품 가격이 누른 제품 가격과 2배 넘게 차이 나면 다른 상품으로 본다.
     * 서버도 price로 같은 검사를 하지만, 서버가 옛 버전이어도 틀린 판단이 나가지 않게 앱에서 한 번 더 막는다.
     */
    private boolean isDifferentProduct(TimingResponse body) {
        long tapped = product.price;
        long tracked = body.analysis.curMin;
        if (tapped <= 0 || tracked <= 0) return false;   // 비교할 가격이 없으면 그대로 보여준다
        double ratio = (double) tracked / tapped;
        return ratio < 0.5 || ratio > 2.0;
    }

    /** 같은 품목 평균과 비교. 품목을 모르거나 통계가 없으면 이 칸은 숨긴 채로 둔다. */
    private void loadPricePosition(long price) {
        if (category == null) return;

        positionCall = ApiClient.get().getPricePosition(category, price);
        positionCall.enqueue(new Callback<PricePositionResponse>() {
            @Override
            public void onResponse(@NonNull Call<PricePositionResponse> call,
                                   @NonNull Response<PricePositionResponse> response) {
                PricePositionResponse p = response.body();
                if (p == null || !p.available || p.vsAvgPct == null) return;

                findViewById(R.id.position_card).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.position_title)).setText(
                        getString(R.string.timing_position_title, category));
                TextView signal = findViewById(R.id.position_signal);
                signal.setText(p.signal);
                signal.setTextColor(colorForSignal(p.signalType));
                ((TextView) findViewById(R.id.position_reason)).setText(
                        p.reason + "\n" + category + " 평균 " + won.format(p.avgPrice) + "원");
            }

            @Override
            public void onFailure(@NonNull Call<PricePositionResponse> call, @NonNull Throwable t) {
                // 보조 정보라서 실패해도 화면에 오류를 띄우지 않는다
            }
        });
    }

    // ── 화면 채우기 ───────────────────────────────────────────

    private void bind(TimingResponse body) {
        Analysis a = body.analysis;
        lastAnalysis = a;
        stateBox.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        // 서버가 다른 판매처의 같은 제품으로 추적했다면 무엇을 기준으로 봤는지 알려준다
        TextView basis = findViewById(R.id.timing_basis);
        if (body.trackedProduct != null && body.trackedProduct.title != null
                && !body.trackedProduct.title.equals(product.title)) {
            basis.setText(getString(R.string.timing_basis, body.trackedProduct.title));
            basis.setVisibility(View.VISIBLE);
        } else {
            basis.setVisibility(View.GONE);
        }

        // ① 판단
        TextView badge = findViewById(R.id.timing_badge);
        badge.setText(a.score);
        badge.getBackground().mutate().setTint(colorForScore(a.color));
        ((TextView) findViewById(R.id.timing_price)).setText(won.format(a.curMin) + "원");
        ((TextView) findViewById(R.id.timing_message)).setText(a.message);

        // ② 그래프
        TextView chartNote = findViewById(R.id.timing_chart_note);
        if (a.hasHistory && body.data != null && body.data.size() >= 2) {
            chart.setVisibility(View.VISIBLE);
            chartNote.setText(R.string.timing_chart_note);
            drawChart(body.data, a.histAvg);
        } else {
            chart.setVisibility(View.GONE);
            chartNote.setText(R.string.timing_chart_empty);
        }

        // ③ 숫자 요약
        setStat(R.id.stat_avg, won.format(a.histAvg) + "원");
        setStat(R.id.stat_min, won.format(a.histMin) + "원" + (a.lowDay != null ? "\n" + shortDate(a.lowDay) : ""));
        setStat(R.id.stat_max, won.format(a.histMax) + "원");
        setStat(R.id.stat_trend, a.trendDir != null ? a.trendDir : "-");
        setStat(R.id.stat_diff, String.format(Locale.KOREA, "%+.1f%%", a.diffPct));
        setStat(R.id.stat_launch, a.launchDiscPct != null
                ? String.format(Locale.KOREA, "%.0f%% 할인", a.launchDiscPct) : "정보 없음");

        // ④ 검색 관심도
        TextView interest = findViewById(R.id.timing_interest);
        if (a.searchSummary != null) {
            double d = a.searchSummary.diffPct;
            String level = d > 10 ? "관심이 높아지는 중이에요. 수요가 몰리면 가격이 오를 수 있어요."
                    : d < -10 ? "관심이 줄어드는 중이에요. 할인이 나올 가능성이 있어요."
                    : "관심도는 평소 수준이에요.";
            interest.setText(String.format(Locale.KOREA,
                    "최근 7일 검색 관심도가 평소보다 %+.0f%%예요. %s", d, level));
            interest.setVisibility(View.VISIBLE);
            findViewById(R.id.timing_interest_label).setVisibility(View.VISIBLE);
        }
    }

    // ── F-11 가격 알림 등록 ───────────────────────────────────

    private void onAlertClick() {
        if (lastAnalysis == null) return;
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(this, "가격 알림은 로그인 후에 쓸 수 있어요", Toast.LENGTH_SHORT).show();
            loginLauncher.launch(new Intent(this, LoginActivity.class));
            return;
        }
        showAlertDialog();
    }

    /** 목표 가격을 입력받는 창. 기본값은 30일 최저가와 현재가의 95% 중 낮은 값 (천 원 단위) */
    private void showAlertDialog() {
        long cur = lastAnalysis.curMin;
        long suggest = Math.min(lastAnalysis.histMin > 0 ? lastAnalysis.histMin : cur, Math.round(cur * 0.95));
        suggest = suggest / 1000 * 1000;

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(suggest));
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_dialog_title)
                .setMessage(getString(R.string.alert_dialog_message, won.format(cur)))
                .setView(box)
                .setNegativeButton("취소", null)
                .setPositiveButton("등록", (d, w) -> {
                    long target;
                    try {
                        target = Long.parseLong(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        target = 0;
                    }
                    if (target <= 0) {
                        Toast.makeText(this, "목표 가격을 숫자로 입력해 주세요", Toast.LENGTH_SHORT).show();
                    } else if (target >= cur) {
                        Toast.makeText(this, "지금 가격(" + won.format(cur) + "원)보다 낮게 정해 주세요",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        createAlert(target);
                    }
                })
                .show();
    }

    private void createAlert(long target) {
        AlertModels.CreateRequest req = new AlertModels.CreateRequest(
                product.title, target, lastAnalysis.curMin, product.link);

        ApiClient.get().createAlert(req).enqueue(new Callback<AlertModels.CreateResponse>() {
            @Override
            public void onResponse(@NonNull Call<AlertModels.CreateResponse> call,
                                   @NonNull Response<AlertModels.CreateResponse> response) {
                if (response.code() == 401) {
                    SessionManager.logout();
                    loginLauncher.launch(new Intent(TimingActivity.this, LoginActivity.class));
                    return;
                }
                if (!response.isSuccessful()) {
                    Toast.makeText(TimingActivity.this,
                            ApiErrors.message(response, "알림을 등록하지 못했어요"), Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(TimingActivity.this,
                        won.format(target) + "원 이하가 되면 알려드릴게요", Toast.LENGTH_LONG).show();
                // 안드로이드 13 이상은 알림 권한이 있어야 알림이 뜬다
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(TimingActivity.this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AlertModels.CreateResponse> call, @NonNull Throwable t) {
                Toast.makeText(TimingActivity.this, "인터넷 연결을 확인해 주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 30일 가격 꺾은선 그래프 + 평균가 기준선 */
    private void drawChart(List<PricePoint> points, long avg) {
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            entries.add(new Entry(i, points.get(i).price));
            labels.add(shortDate(points.get(i).period));
        }

        int primary = ContextCompat.getColor(this, R.color.timing_line);
        int textColor = ContextCompat.getColor(this, R.color.timing_chart_text);

        LineDataSet set = new LineDataSet(entries, "최저가");
        set.setColor(primary);
        set.setLineWidth(2f);
        set.setDrawCircles(points.size() <= 10);   // 점이 많으면 동그라미는 생략
        set.setCircleColor(primary);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillColor(primary);
        set.setFillAlpha(30);
        set.setHighLightColor(primary);

        chart.setData(new LineData(set));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.setScaleEnabled(false);
        chart.setNoDataText("");

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setLabelCount(5);
        x.setTextColor(textColor);
        x.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int i = Math.round(value);
                return (i >= 0 && i < labels.size()) ? labels.get(i) : "";
            }
        });

        YAxis y = chart.getAxisLeft();
        y.setTextColor(textColor);
        y.setLabelCount(4);
        y.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return won.format(Math.round(value / 10000f)) + "만";
            }
        });

        // 30일 평균 기준선: 선 아래면 평균보다 싼 날
        y.removeAllLimitLines();
        LimitLine avgLine = new LimitLine(avg, "30일 평균");
        avgLine.setLineColor(Color.GRAY);
        avgLine.setLineWidth(1f);
        avgLine.enableDashedLine(10f, 8f, 0f);
        avgLine.setTextColor(textColor);
        avgLine.setTextSize(10f);
        y.addLimitLine(avgLine);
        // 평균선이 그래프 밖으로 나가지 않게 범위를 넓힌다
        y.setAxisMinimum(Math.min(set.getYMin(), avg) * 0.97f);
        y.setAxisMaximum(Math.max(set.getYMax(), avg) * 1.03f);

        chart.animateX(600);
        chart.invalidate();
    }

    private void showError(String text) {
        stateBox.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
        retryButton.setVisibility(View.VISIBLE);
    }

    // ── 도우미 ───────────────────────────────────────────────

    private void setStat(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    /** "2026-11-12" → "11.12" */
    private String shortDate(String d) {
        if (d == null || d.length() < 10) return d == null ? "" : d;
        return d.substring(5, 7) + "." + d.substring(8, 10);
    }

    private int colorForScore(String color) {
        if ("green".equals(color)) return ContextCompat.getColor(this, R.color.score_green);
        if ("blue".equals(color)) return ContextCompat.getColor(this, R.color.score_blue);
        if ("yellow".equals(color)) return ContextCompat.getColor(this, R.color.score_yellow);
        return ContextCompat.getColor(this, R.color.score_red);
    }

    private int colorForSignal(String type) {
        if ("buy".equals(type)) return ContextCompat.getColor(this, R.color.score_green);
        if ("wait".equals(type)) return ContextCompat.getColor(this, R.color.score_red);
        return ContextCompat.getColor(this, R.color.score_blue);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (timingCall != null) timingCall.cancel();
        if (positionCall != null) positionCall.cancel();
        super.onDestroy();
    }
}
