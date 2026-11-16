package com.example.gajeontiming;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.gajeontiming.data.RecentProductDb;
import com.example.gajeontiming.model.AiAnalysisResponse;
import com.example.gajeontiming.model.AiAnalysisResponse.Analysis;
import com.example.gajeontiming.model.AiAnalysisResponse.Review;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.model.ReportResponse;
import com.example.gajeontiming.model.ReportResponse.NewsItem;
import com.example.gajeontiming.model.TrendItem;
import com.example.gajeontiming.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-05 제품 리포트 화면.
 * 제품 목록 또는 홈에서 Product 객체를 인텐트로 받는다.
 * 기본 정보는 받은 값으로 바로 보여주고, AI 분석 · 후기 · 뉴스는 서버에서 따로 불러와 채운다.
 */
public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "product";
    public static final String EXTRA_CATEGORY = "category";   // 선택: 들어온 화면이 알고 있는 품목

    private static final int MAX_REVIEWS = 5;
    private static final int MAX_NEWS = 5;

    private Product product;
    private String categoryHint;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    // AI 분석 영역
    private ProgressBar aiProgress;
    private TextView aiState;
    private Button aiRetry;
    private View aiContent;

    // 후기 · 뉴스 영역
    private LinearLayout reviewContainer;
    private TextView reviewEmpty;
    private LinearLayout newsContainer;
    private TextView newsState;

    private Call<AiAnalysisResponse> aiCall;
    private Call<ReportResponse> reportCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        product = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        categoryHint = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (product == null) {          // 잘못 들어온 경우 화면을 닫는다
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.detail_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        aiProgress = findViewById(R.id.ai_progress);
        aiState = findViewById(R.id.ai_state);
        aiRetry = findViewById(R.id.ai_retry);
        aiContent = findViewById(R.id.ai_content);
        reviewContainer = findViewById(R.id.review_container);
        reviewEmpty = findViewById(R.id.review_empty);
        newsContainer = findViewById(R.id.news_container);
        newsState = findViewById(R.id.news_state);

        bindBasicInfo();
        RecentProductDb.get(this).add(product, resolveCategory());   // F-08 최근 본 제품에 기록

        aiRetry.setOnClickListener(v -> loadAiAnalysis());
        loadAiAnalysis();
        loadNews();
    }

    // ── 기본 정보 (인텐트로 받은 값) ─────────────────────────────

    private void bindBasicInfo() {
        ImageView image = findViewById(R.id.detail_image);
        TextView title = findViewById(R.id.detail_title);
        TextView price = findViewById(R.id.detail_price);
        TextView meta = findViewById(R.id.detail_meta);

        Glide.with(this).load(product.image).centerInside().into(image);
        title.setText(product.title);
        price.setText(product.price > 0 ? won.format(product.price) + "원" : "가격 정보 없음");

        StringBuilder sb = new StringBuilder();
        if (notEmpty(product.brand)) sb.append(product.brand.trim());
        if (notEmpty(product.mallName)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(product.mallName.trim()).append(" 최저가");
        }
        if (product.reviewCount > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("리뷰 ").append(won.format(product.reviewCount));
            if (product.reviewScore > 0) sb.append(" · ★").append(product.reviewScore);
        }
        meta.setText(sb);
        meta.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);

        Button shop = findViewById(R.id.detail_shop_button);
        shop.setVisibility(notEmpty(product.link) ? View.VISIBLE : View.GONE);
        shop.setOnClickListener(v -> openLink(product.link));

        // 다음 단계에서 연결할 화면들
        findViewById(R.id.detail_timing_button).setOnClickListener(v -> openTiming());
        findViewById(R.id.detail_compare_button).setOnClickListener(v -> openCompare());
    }

    /** 품목 이름: 목록에서 넘겨받은 값 → 트렌드의 품목 → 제품명에서 찾기 순서로 정한다 */
    private String resolveCategory() {
        String category = categoryHint;
        if (category == null && product instanceof TrendItem) {
            category = ((TrendItem) product).category;
        }
        if (category == null) {
            category = Categories.detect(product.title);
        }
        return category;
    }

    /** F-06 구매 타이밍 화면으로 제품과 품목 이름을 넘긴다 */
    private void openTiming() {
        Intent intent = new Intent(this, TimingActivity.class);
        intent.putExtra(TimingActivity.EXTRA_PRODUCT, product);
        intent.putExtra(TimingActivity.EXTRA_CATEGORY, resolveCategory());
        startActivity(intent);
    }

    /** F-07 제품 비교 화면으로 이 제품을 A로 넘긴다 */
    private void openCompare() {
        Intent intent = new Intent(this, CompareActivity.class);
        intent.putExtra(CompareActivity.EXTRA_PRODUCT, product);
        intent.putExtra(CompareActivity.EXTRA_CATEGORY, resolveCategory());
        startActivity(intent);
    }

    // ── AI 분석 + 후기 ─────────────────────────────────────────

    private void loadAiAnalysis() {
        aiProgress.setVisibility(View.VISIBLE);
        aiState.setVisibility(View.VISIBLE);
        aiState.setText(R.string.detail_ai_loading);
        aiRetry.setVisibility(View.GONE);
        aiContent.setVisibility(View.GONE);
        reviewEmpty.setText(R.string.detail_review_loading);

        aiCall = ApiClient.get().getAiAnalysis(product.title);
        aiCall.enqueue(new Callback<AiAnalysisResponse>() {
            @Override
            public void onResponse(@NonNull Call<AiAnalysisResponse> call,
                                   @NonNull Response<AiAnalysisResponse> response) {
                AiAnalysisResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    showAiError(getString(R.string.detail_ai_failed));
                    bindReviews(null);
                    return;
                }
                if (body.analysis == null) {
                    // 후기가 너무 적으면 서버가 분석 없이 돌려준다
                    showAiError(getString(R.string.detail_ai_no_data));
                } else {
                    bindAnalysis(body.analysis);
                }
                bindReviews(body.reviews);
            }

            @Override
            public void onFailure(@NonNull Call<AiAnalysisResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                showAiError(getString(R.string.detail_ai_failed));
                bindReviews(null);
            }
        });
    }

    private void bindAnalysis(Analysis a) {
        aiProgress.setVisibility(View.GONE);
        aiState.setVisibility(View.GONE);
        aiRetry.setVisibility(View.GONE);
        aiContent.setVisibility(View.VISIBLE);

        TextView score = findViewById(R.id.ai_score);
        TextView summary = findViewById(R.id.ai_summary);
        TextView warning = findViewById(R.id.ai_warning);

        score.setText(a.score > 0 ? String.format(Locale.KOREA, "★ %.1f", a.score) : "");
        score.setVisibility(a.score > 0 ? View.VISIBLE : View.GONE);
        setOrHide(summary, a.summary);

        // 리콜 · 반복 결함은 눈에 띄게 맨 위에
        StringBuilder warn = new StringBuilder();
        if (a.recall) warn.append("리콜 관련 뉴스가 있어요. 구매 전에 꼭 확인하세요.");
        if (a.defects != null && !a.defects.isEmpty()) {
            if (warn.length() > 0) warn.append("\n");
            warn.append("여러 후기에서 반복된 문제: ").append(String.join(", ", a.defects));
        }
        setOrHide(warning, warn.toString());

        setBullets(R.id.ai_pros_label, R.id.ai_pros, a.pros);
        setBullets(R.id.ai_cons_label, R.id.ai_cons, a.cons);
        setBullets(R.id.ai_cautions_label, R.id.ai_cautions, a.cautions);

        TextView suitableLabel = findViewById(R.id.ai_suitable_label);
        TextView suitable = findViewById(R.id.ai_suitable);
        setOrHide(suitable, a.suitableFor);
        suitableLabel.setVisibility(suitable.getVisibility());
    }

    private void showAiError(String text) {
        aiProgress.setVisibility(View.GONE);
        aiContent.setVisibility(View.GONE);
        aiState.setVisibility(View.VISIBLE);
        aiState.setText(text);
        aiRetry.setVisibility(View.VISIBLE);
    }

    private void bindReviews(List<Review> reviews) {
        reviewContainer.removeAllViews();
        if (reviews == null || reviews.isEmpty()) {
            reviewEmpty.setText(R.string.detail_review_empty);
            reviewEmpty.setVisibility(View.VISIBLE);
            return;
        }
        reviewEmpty.setVisibility(View.GONE);
        for (int i = 0; i < Math.min(MAX_REVIEWS, reviews.size()); i++) {
            Review r = reviews.get(i);
            addLinkRow(reviewContainer,
                    r.source + " · " + formatDate(r.date),
                    r.title,
                    r.review,
                    r.link);
        }
    }

    // ── 관련 뉴스 ─────────────────────────────────────────────

    private void loadNews() {
        newsState.setVisibility(View.VISIBLE);
        newsState.setText(R.string.detail_news_loading);

        reportCall = ApiClient.get().getReport(product.title);
        reportCall.enqueue(new Callback<ReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReportResponse> call,
                                   @NonNull Response<ReportResponse> response) {
                ReportResponse body = response.body();
                List<NewsItem> items = (body != null && body.news != null) ? body.news.items : null;
                if (items == null || items.isEmpty()) {
                    newsState.setText(R.string.detail_news_empty);
                    return;
                }
                newsState.setVisibility(View.GONE);
                newsContainer.removeAllViews();
                for (int i = 0; i < Math.min(MAX_NEWS, items.size()); i++) {
                    NewsItem n = items.get(i);
                    addLinkRow(newsContainer, shortPubDate(n.pubDate), n.title, null, n.link);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReportResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                newsState.setText(R.string.detail_news_failed);
            }
        });
    }

    // ── 공통 도우미 ───────────────────────────────────────────

    /** 후기 · 뉴스 한 줄(item_link_row.xml)을 만들어 붙인다. 누르면 원문 링크를 연다. */
    private void addLinkRow(LinearLayout container, String top, String title, String body, String link) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_link_row, container, false);
        setOrHide(row.findViewById(R.id.row_top), top);
        setOrHide(row.findViewById(R.id.row_title), title);
        setOrHide(row.findViewById(R.id.row_body), body);
        if (notEmpty(link)) {
            row.setOnClickListener(v -> openLink(link));
        }
        container.addView(row);
    }

    private void setBullets(int labelId, int textId, List<String> lines) {
        TextView label = findViewById(labelId);
        TextView text = findViewById(textId);
        if (lines == null || lines.isEmpty()) {
            label.setVisibility(View.GONE);
            text.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("• ").append(line);
        }
        label.setVisibility(View.VISIBLE);
        text.setVisibility(View.VISIBLE);
        text.setText(sb);
    }

    private void setOrHide(TextView view, String text) {
        if (notEmpty(text)) {
            view.setVisibility(View.VISIBLE);
            view.setText(text.trim());
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** "20261105" → "2026.11.05" */
    private String formatDate(String d) {
        if (d == null || d.length() != 8) return d == null ? "" : d;
        return d.substring(0, 4) + "." + d.substring(4, 6) + "." + d.substring(6);
    }

    /** "Thu, 12 Nov 2026 09:30:00 +0900" → "12 Nov 2026" */
    private String shortPubDate(String d) {
        if (d == null) return "";
        String[] parts = d.split(" ");
        return parts.length >= 4 ? parts[1] + " " + parts[2] + " " + parts[3] : d;
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {   // 들어온 화면(목록 또는 홈)으로 돌아간다
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (aiCall != null) aiCall.cancel();
        if (reportCall != null) reportCall.cancel();
        super.onDestroy();
    }
}
