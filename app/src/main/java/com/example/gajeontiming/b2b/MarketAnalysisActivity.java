package com.example.gajeontiming.b2b;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.gajeontiming.LoginActivity;
import com.example.gajeontiming.R;
import com.example.gajeontiming.model.MarketModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.AuthGuard;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * B2B 제품 시장 분석 2/2 (단계 4, 승인된 사업자 회원 전용).
 * GET /api/b2b/search-analysis?q=제품명&hint=검색어 → 웹 분석 화면(B2BAnalysis)과 같은 5개 카드.
 *   제품 기본 정보 · 비슷한 제품 · 경쟁 제품 비교 · 검색량 추이 · 실사용 후기
 * 예측 · AI 판단 · 매입 판단은 보여 주지 않고 실제 데이터만. 값이 없는 줄 · 카드는 통째로 숨긴다.
 * 분석은 가격 · 후기 수집까지 10초~1분 걸린다 (서버가 결과를 캐시해서 두 번째부터는 빠르다).
 * 401 → 로그인 후 다시, 403 · 402 → 서버 문구만.
 */
public class MarketAnalysisActivity extends AppCompatActivity {

    public static final String EXTRA_QUERY = "query";   // 고른 제품 이름
    public static final String EXTRA_HINT = "hint";     // 사용자가 입력한 검색어

    private static final long LOADING_STEP_MS = 7000;

    public static void start(Context context, String productTitle, String searchQuery) {
        context.startActivity(new Intent(context, MarketAnalysisActivity.class)
                .putExtra(EXTRA_QUERY, productTitle)
                .putExtra(EXTRA_HINT, searchQuery));
    }

    private String query, hint;
    private View scroll, loading, errorBox;
    private LinearLayout content;
    private TextView loadingText, errorText;
    private Call<MarketModels.Analysis> call;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int loadingStep;
    private boolean lastForce;

    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) load(lastForce);
                else finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market_analysis);

        query = getIntent().getStringExtra(EXTRA_QUERY);
        hint = getIntent().getStringExtra(EXTRA_HINT);
        if (query == null || query.isEmpty()) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.analysis_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        scroll = findViewById(R.id.analysis_scroll);
        content = findViewById(R.id.analysis_content);
        loading = findViewById(R.id.analysis_loading);
        loadingText = findViewById(R.id.analysis_loading_text);
        errorBox = findViewById(R.id.analysis_error);
        errorText = findViewById(R.id.analysis_error_text);
        findViewById(R.id.analysis_retry).setOnClickListener(v -> load(lastForce));

        load(false);
    }

    // ── 불러오기 ──────────────────────────────────────────────

    private void load(boolean force) {
        lastForce = force;
        if (call != null) call.cancel();
        scroll.setVisibility(View.GONE);
        errorBox.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        startLoadingSteps();

        call = ApiClient.get().getMarketAnalysis(query, hint == null ? "" : hint, force);
        call.enqueue(new Callback<MarketModels.Analysis>() {
            @Override
            public void onResponse(@NonNull Call<MarketModels.Analysis> c,
                                   @NonNull Response<MarketModels.Analysis> response) {
                stopLoadingSteps();
                if (AuthGuard.isLoggedOut(response)) {
                    loginLauncher.launch(new Intent(MarketAnalysisActivity.this, LoginActivity.class));
                    return;
                }
                MarketModels.Analysis body = response.body();
                if (!response.isSuccessful() || body == null) {
                    // 403(승인 전 · 거절) · 402(등급)는 다시 해도 같아서 다시 시도 버튼을 숨긴다
                    boolean blocked = response.code() == 403 || response.code() == 402;
                    showError(AuthGuard.message(response, getString(R.string.analysis_failed)), !blocked);
                    return;
                }
                loading.setVisibility(View.GONE);
                if (body.unsupported) renderPickCategory();
                else render(body);
                scroll.setVisibility(View.VISIBLE);
                scroll.scrollTo(0, 0);
            }

            @Override
            public void onFailure(@NonNull Call<MarketModels.Analysis> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                stopLoadingSteps();
                showError(getString(R.string.analysis_failed_network), true);
            }
        });
    }

    private void showError(String text, boolean canRetry) {
        loading.setVisibility(View.GONE);
        errorText.setText(text);
        findViewById(R.id.analysis_retry).setVisibility(canRetry ? View.VISIBLE : View.GONE);
        errorBox.setVisibility(View.VISIBLE);
    }

    /** "가격 정보를 모으는 중…" → "검색량 추이를…" → … 7초마다 다음 문구 (마지막 문구에서 멈춤) */
    private final Runnable nextLoadingStep = new Runnable() {
        @Override
        public void run() {
            String[] steps = getResources().getStringArray(R.array.analysis_loading_steps);
            loadingText.setText(steps[Math.min(loadingStep, steps.length - 1)]);
            if (++loadingStep < steps.length) handler.postDelayed(this, LOADING_STEP_MS);
        }
    };

    private void startLoadingSteps() {
        stopLoadingSteps();
        loadingStep = 0;
        nextLoadingStep.run();
    }

    private void stopLoadingSteps() {
        handler.removeCallbacks(nextLoadingStep);
    }

    // ── 화면 그리기 ────────────────────────────────────────────

    private void render(MarketModels.Analysis a) {
        content.removeAllViews();
        renderProduct(a);
        renderSimilar(a.similarProducts);
        renderCompetitor(a, a.competitorComparison);
        renderInterest(a.interestForecast, a.trendKeyword);
        renderReviews(a.reviewSentiment);
        TextView note = text(content, getString(R.string.analysis_footer), 12);
        note.setPadding(dp(20), dp(16), dp(20), 0);
        note.setTextColor(muted());
    }

    /**
     * 제품명 · 검색어에 품목 단어(냉장고 등)가 없으면 서버가 분석하지 않는다 (unsupported).
     * 예: "삼성전자 비스포크 RF85DB9…" → 품목을 고르게 해서 hint로 다시 분석한다.
     */
    private void renderPickCategory() {
        content.removeAllViews();
        LinearLayout body = section(getString(R.string.analysis_pick_title), null);
        paragraph(body, query).setTypeface(Typeface.DEFAULT_BOLD);
        paragraph(body, getString(R.string.analysis_pick_body));

        ChipGroup chips = new ChipGroup(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        body.addView(chips, lp);
        for (String kw : AnalysisFormat.CATEGORY_HINTS) {
            Chip chip = new Chip(this);
            chip.setText(AnalysisFormat.chipLabel(kw));
            chip.setOnClickListener(v -> {
                hint = kw;   // "새로 분석"도 고른 품목으로
                load(false);
            });
            chips.addView(chip);
        }
    }

    /** 1. 제품 기본 정보: 사진 + 제품명, 그 아래 값이 있는 줄만 */
    private void renderProduct(MarketModels.Analysis a) {
        MarketModels.ProductInfo p = a.productInfo;
        LinearLayout body = section(getString(R.string.analysis_product), getString(R.string.analysis_product_sub));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));
        body.addView(row);
        image(row, p != null ? p.image : null, 72);
        TextView name = text(row, p != null && p.name != null ? p.name : query, 15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        keyValue(body, getString(R.string.analysis_model), a.modelNumber);
        if (p == null) return;
        keyValue(body, getString(R.string.analysis_brand), p.brand);
        keyValue(body, getString(R.string.analysis_release), AnalysisFormat.month(p.releaseDate));
        // 진짜 출시가가 없으면 다나와 24개월 최고가를 "최고가"라고 밝혀서 보여 준다
        String launch = AnalysisFormat.won(p.launchPrice);
        if (launch != null) keyValue(body, getString(R.string.analysis_launch_price), launch);
        else keyValue(body, getString(R.string.analysis_max_price), AnalysisFormat.won(p.maxPrice24m));
        TextView current = keyValue(body, getString(R.string.analysis_current_price), AnalysisFormat.won(p.currentPrice));
        if (current != null) current.setTextColor(ContextCompat.getColor(this, R.color.score_green));
    }

    /** 2. 비슷한 제품 (최대 5개): 사진 · 제품명 · 브랜드 · 모델 · 용량 · 가격, 누르면 판매 페이지 */
    private void renderSimilar(List<MarketModels.SimilarProduct> items) {
        if (items == null || items.isEmpty()) return;
        boolean byCapacity = false;
        for (MarketModels.SimilarProduct it : items) if (notEmpty(it.capacity)) byCapacity = true;
        LinearLayout body = section(getString(R.string.analysis_similar), getString(byCapacity
                ? R.string.analysis_similar_sub_capacity : R.string.analysis_similar_sub_price));

        int shown = 0;
        for (MarketModels.SimilarProduct it : items) {
            if (shown++ >= 5) break;
            LinearLayout row = linkRow(body, it.link);
            image(row, it.image, 52);
            LinearLayout col = column(row);
            TextView title = text(col, it.title, 14);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            StringBuilder meta = new StringBuilder();
            join(meta, it.brand);
            join(meta, it.modelNumber);
            if (notEmpty(it.capacity)) join(meta, getString(R.string.analysis_capacity, it.capacity.trim()));
            if (meta.length() > 0) text(col, meta, 12).setTextColor(muted());
            String price = AnalysisFormat.won(it.price);
            if (price != null) {
                TextView pv = text(row, price, 14);
                pv.setTypeface(Typeface.DEFAULT_BOLD);
                pv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    /**
     * 3. 경쟁 제품 비교: 검색한 제품 · 경쟁 제품 각각의 장단점 (실사용 후기 요약).
     * 서버의 추천(recommended · recommendation_reason)은 쓰지 않는다. 경쟁 제품이 없으면 카드째 숨김.
     */
    private void renderCompetitor(MarketModels.Analysis a, MarketModels.Comparison c) {
        if (c == null || c.competitor == null) return;
        MarketModels.Competitor comp = c.competitor;
        LinearLayout body = section(getString(R.string.analysis_competitor), getString(notEmpty(comp.matchedCapacity)
                ? R.string.analysis_competitor_sub_capacity : R.string.analysis_competitor_sub_price));

        MarketModels.ProductInfo p = a.productInfo;
        StringBuilder mine = new StringBuilder();
        join(mine, a.modelNumber);
        join(mine, p != null ? AnalysisFormat.won(p.currentPrice) : null);
        compareColumn(body, p != null ? p.image : null, getString(R.string.analysis_selected), null,
                mine, c.selectedPros, c.selectedCons);

        StringBuilder theirs = new StringBuilder();
        join(theirs, comp.modelNumber);
        if (notEmpty(comp.matchedCapacity)) join(theirs, getString(R.string.analysis_capacity, comp.matchedCapacity.trim()));
        join(theirs, AnalysisFormat.won(comp.price));
        String name = notEmpty(comp.brand) ? getString(R.string.analysis_competitor_name, comp.brand.trim())
                : getString(R.string.analysis_competitor_fallback);
        compareColumn(body, comp.image, name, comp.link, theirs, c.competitorPros, c.competitorCons);
    }

    /** 경쟁 제품 비교의 한 쪽: 사진 · 이름 · 모델/가격 + "+ 장점" · "− 단점" */
    private void compareColumn(LinearLayout parent, String imageUrl, String title, String link,
                               CharSequence meta, List<String> pros, List<String> cons) {
        LinearLayout head = link != null ? linkRow(parent, link) : plainRow(parent);
        image(head, imageUrl, 44);
        LinearLayout col = column(head);
        text(col, title, 14).setTypeface(Typeface.DEFAULT_BOLD);
        if (meta.length() > 0) text(col, meta, 12).setTextColor(muted());

        int good = ContextCompat.getColor(this, R.color.score_green);
        int bad = ContextCompat.getColor(this, R.color.score_red);
        if (pros != null) for (String s : pros) {
            TextView t = paragraph(parent, "+ " + s);
            if (t != null) { t.setTextColor(good); t.setPadding(0, dp(4), 0, 0); }
        }
        if (cons != null) for (String s : cons) {
            TextView t = paragraph(parent, "− " + s);
            if (t != null) { t.setTextColor(bad); t.setPadding(0, dp(4), 0, 0); }
        }
    }

    /** 4. 검색량 추이: 네이버 데이터랩 실제 값만 (예측선 · 추세선 · 예측 문장 없음) */
    private void renderInterest(MarketModels.Interest fc, String keyword) {
        if (fc == null || fc.history == null) return;
        List<String> labels = new ArrayList<>();
        List<Float> values = new ArrayList<>();
        for (MarketModels.TrendPoint p : fc.history) {
            if (p.ratio == null) continue;
            labels.add(AnalysisFormat.shortDate(p.period));
            values.add(p.ratio.floatValue());
        }
        if (values.size() < 2) return;
        LinearLayout body = section(getString(R.string.analysis_interest), notEmpty(keyword)
                ? getString(R.string.analysis_interest_sub, keyword.trim())
                : getString(R.string.analysis_interest_sub_plain));
        chart(body, labels, values);
        TextView legend = text(body, getString(R.string.analysis_interest_legend), 12);
        legend.setTextColor(muted());
        legend.setPadding(0, dp(6), 0, 0);
    }

    /** 5. 실사용 후기: 블로그 · 카페 글 제목, 누르면 원문. 4개만 보이고 나머지는 "전체 보기" */
    private void renderReviews(MarketModels.ReviewSentiment r) {
        if (r == null || r.reviews == null) return;
        List<MarketModels.Review> reviews = new ArrayList<>();
        for (MarketModels.Review rv : r.reviews) if (notEmpty(rv.title) || notEmpty(rv.text)) reviews.add(rv);
        if (reviews.isEmpty()) return;
        LinearLayout body = section(getString(R.string.analysis_reviews),
                getString(R.string.analysis_reviews_sub, reviews.size()));

        List<View> extra = new ArrayList<>();
        for (int i = 0; i < reviews.size(); i++) {
            MarketModels.Review rv = reviews.get(i);
            LinearLayout row = linkRow(body, rv.link);
            LinearLayout col = column(row);
            if (notEmpty(rv.source)) text(col, rv.source.trim(), 12).setTextColor(muted());
            TextView title = text(col, notEmpty(rv.title) ? rv.title.trim() : rv.text.trim(), 14);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            if (i >= 4) {
                row.setVisibility(View.GONE);
                extra.add(row);
            }
        }
        if (extra.isEmpty()) return;
        MaterialButton more = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        more.setText(getString(R.string.analysis_reviews_more, reviews.size()));
        more.setOnClickListener(v -> {
            boolean open = extra.get(0).getVisibility() != View.VISIBLE;
            for (View e : extra) e.setVisibility(open ? View.VISIBLE : View.GONE);
            more.setText(open ? getString(R.string.analysis_reviews_less)
                    : getString(R.string.analysis_reviews_more, reviews.size()));
        });
        body.addView(more, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ── 조각 만들기 ────────────────────────────────────────────

    /** 섹션 카드를 붙이고 내용 칸을 돌려준다. subtitle이 없으면 부제 줄을 숨긴다 */
    private LinearLayout section(String title, String subtitle) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_analysis_section, content, false);
        ((TextView) card.findViewById(R.id.section_title)).setText(title);
        if (notEmpty(subtitle)) {
            TextView sub = card.findViewById(R.id.section_subtitle);
            sub.setText(subtitle);
            sub.setVisibility(View.VISIBLE);
        }
        content.addView(card);
        return card.findViewById(R.id.section_body);
    }

    private TextView text(LinearLayout parent, CharSequence value, int sp) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        t.setLineSpacing(dp(3), 1f);
        parent.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** 본문 한 덩어리. 내용이 없으면 아무것도 붙이지 않고 null */
    private TextView paragraph(LinearLayout parent, CharSequence value) {
        if (value == null || value.toString().trim().isEmpty()) return null;
        TextView t = text(parent, value, 14);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    /** "이름 ··· 값" 한 줄. 값이 없으면 줄을 만들지 않고 null, 있으면 값 TextView */
    private TextView keyValue(LinearLayout parent, String key, String value) {
        if (!notEmpty(value) || key == null) return null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        parent.addView(row);
        TextView k = text(row, key, 14);
        k.setTextColor(muted());
        k.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView v = text(row, value.trim(), 14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    /** 가로 한 줄 (사진 · 글 · 가격) */
    private LinearLayout plainRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** 누르면 링크(판매 페이지 · 후기 원문)를 브라우저로 여는 줄. 링크가 없으면 그냥 줄 */
    private LinearLayout linkRow(LinearLayout parent, String link) {
        LinearLayout row = plainRow(parent);
        if (!notEmpty(link)) return row;
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        row.setOnClickListener(v -> openLink(link));
        return row;
    }

    /** 줄 안에서 남은 폭을 채우는 세로 칸 */
    private LinearLayout column(LinearLayout row) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(dp(8));
        row.addView(col, lp);
        return col;
    }

    /** 정사각 사진. 주소가 없으면 자리도 만들지 않는다 */
    private void image(LinearLayout row, String url, int sizeDp) {
        if (!notEmpty(url)) return;
        ImageView iv = new ImageView(this);
        iv.setContentDescription(getString(R.string.analysis_product_image));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.setMarginEnd(dp(12));
        row.addView(iv, lp);
        Glide.with(this).load(url).centerInside().into(iv);
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            // 브라우저가 없는 기기: 아무것도 하지 않는다
        }
    }

    /** 검색량 꺾은선 그래프 (실제 값만). 세로축은 0부터 (관심도는 기간 최댓값 = 100인 상대값) */
    private void chart(LinearLayout parent, List<String> labels, List<Float> values) {
        LineChart chart = new LineChart(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        lp.topMargin = dp(10);
        parent.addView(chart, lp);

        int primary = ContextCompat.getColor(this, R.color.timing_line);
        int textColor = ContextCompat.getColor(this, R.color.timing_chart_text);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) entries.add(new Entry(i, values.get(i)));
        LineDataSet set = new LineDataSet(entries, "");
        set.setColor(primary);
        set.setLineWidth(2f);
        set.setDrawCircles(values.size() <= 16);
        set.setCircleColor(primary);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillColor(primary);
        set.setFillAlpha(30);

        chart.setData(new LineData(set));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.setScaleEnabled(false);
        chart.setTouchEnabled(false);
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
        y.setAxisMinimum(0f);
        y.setAxisMaximum(Math.max(set.getYMax() * 1.08f, 1f));
        chart.invalidate();
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void join(StringBuilder sb, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(value.trim());
    }

    private int muted() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ── 메뉴 · 정리 ────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_analysis, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.analysis_refresh) {
            load(true);   // 캐시 무시하고 가격 · 후기 · 경쟁 제품을 새로
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (call != null) call.cancel();
        stopLoadingSteps();
        super.onDestroy();
    }
}
