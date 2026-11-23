package com.example.gajeontiming;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AiCompareResponse;
import com.example.gajeontiming.model.BestPriceResponse;
import com.example.gajeontiming.model.CategoryResponse;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-07 제품 비교 화면 (기본 비교).
 * 리포트에서 제품 A를 받고, 제품 목록 화면을 "고르기 모드"로 열어 제품 B를 돌려받는다.
 * B 목록은 A와 같은 품목 전체(브랜드 칩 · 정렬 칩). A의 품목을 모르면 GET /api/category로 먼저 묻는다.
 * 두 제품의 지금 최저가와 기본 정보를 나란히 놓고, 어느 쪽이 얼마나 싼지 알려준다.
 */
public class CompareActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "product";
    public static final String EXTRA_CATEGORY = "category";

    private Product productA;
    private Product productB;
    private String category;

    // 최저가 API 결과 (못 받았으면 목록에 있던 가격을 쓴다)
    private BestPriceResponse bestA;
    private BestPriceResponse bestB;

    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    private View emptyBox;
    private View resultBox;
    private View tableBox;      // 결론 · 비교표 · 버튼 묶음 (B를 고르기 전에는 숨김)
    private LinearLayout rows;
    private TextView verdict;

    private Call<CategoryResponse> categoryCall;
    private View categoryLoading;
    private View pickButton;

    private Call<BestPriceResponse> callA;
    private Call<BestPriceResponse> callB;

    // F-13 AI 비교 영역
    private Button aiButton;
    private ProgressBar aiProgress;
    private TextView aiState;
    private View aiResult;
    private Call<AiCompareResponse> aiCall;

    /** 로그인하지 않은 상태에서 AI 비교를 누르면 로그인 화면을 거쳐 바로 이어서 실행한다 */
    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) runAiCompare();
            });

    /** 제품 목록(고르기 모드)에서 고른 제품을 돌려받는다 */
    private final ActivityResultLauncher<Intent> pickLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Product picked = (Product) result.getData()
                        .getSerializableExtra(ProductListActivity.RESULT_PRODUCT);
                if (picked == null) return;
                if (isSameProduct(productA, picked)) {
                    Toast.makeText(this, "같은 제품이에요. 다른 제품을 골라 주세요", Toast.LENGTH_SHORT).show();
                    return;
                }
                productB = picked;
                bestB = null;
                resetAi();          // B가 바뀌면 이전 AI 결과는 더 이상 맞지 않는다
                showComparison();
                loadBestPrice(false);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);

        productA = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (productA == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.compare_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        emptyBox = findViewById(R.id.compare_empty);
        resultBox = findViewById(R.id.compare_result);
        tableBox = findViewById(R.id.compare_table);
        rows = findViewById(R.id.compare_rows);
        verdict = findViewById(R.id.compare_verdict);

        bindHeader(R.id.compare_a, productA);
        categoryLoading = findViewById(R.id.compare_category_loading);
        pickButton = findViewById(R.id.compare_pick_button);
        pickButton.setOnClickListener(v -> openPicker());
        findViewById(R.id.compare_change_button).setOnClickListener(v -> openPicker());

        aiButton = findViewById(R.id.ai_compare_button);
        aiProgress = findViewById(R.id.ai_compare_progress);
        aiState = findViewById(R.id.ai_compare_state);
        aiResult = findViewById(R.id.ai_compare_result);
        aiButton.setOnClickListener(v -> onAiCompareClick());

        emptyBox.setVisibility(View.VISIBLE);
        resultBox.setVisibility(View.GONE);
        tableBox.setVisibility(View.GONE);
        if (category != null) {
            loadBestPrice(true);
        } else {
            loadCategory();
        }
    }

    // ── 첫 제품의 품목 ────────────────────────────────────────

    /**
     * 상품명에 품목 단어가 없으면(예: "LG 뉴 스타일러") 앱이 품목을 모른다 → 서버에 묻는다.
     * 묻는 동안은 "고르기" 버튼 대신 로딩을 보여 준다. 실패 · null이면 브랜드 검색으로 넘어간다.
     */
    private void loadCategory() {
        setCategoryLoading(true);
        categoryCall = ApiClient.get().getCategory(productA.title);
        categoryCall.enqueue(new Callback<CategoryResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryResponse> call,
                                   @NonNull Response<CategoryResponse> response) {
                CategoryResponse body = response.body();
                if (body != null && body.category != null && !body.category.trim().isEmpty()) {
                    category = body.category.trim();
                }
                onCategoryResolved();
            }

            @Override
            public void onFailure(@NonNull Call<CategoryResponse> call, @NonNull Throwable t) {
                if (!call.isCanceled()) onCategoryResolved();
            }
        });
    }

    private void onCategoryResolved() {
        if (isFinishing()) return;
        setCategoryLoading(false);
        loadBestPrice(true);   // 품목을 알면 최저가도 소모품을 빼고 찾는다
    }

    private void setCategoryLoading(boolean on) {
        categoryLoading.setVisibility(on ? View.VISIBLE : View.GONE);
        pickButton.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    // ── 제품 B 고르기 ─────────────────────────────────────────

    private void openPicker() {
        Intent intent = new Intent(this, ProductListActivity.class);
        if (category != null) {
            // 같은 품목 전체 (검색어 = 품목 이름 → 목록 화면이 브랜드 칩 · 품목 안 검색을 켠다)
            intent.putExtra(ProductListActivity.EXTRA_QUERY, category);
            intent.putExtra(ProductListActivity.EXTRA_CATEGORY, category);
        } else {
            // 품목을 끝내 모르면 A의 브랜드(없으면 제품명 첫 단어)로 검색하고 그렇다고 알린다
            String brand = (productA.brand != null && !productA.brand.trim().isEmpty())
                    ? productA.brand.trim()
                    : productA.title.split(" ")[0];
            intent.putExtra(ProductListActivity.EXTRA_QUERY, brand);
            intent.putExtra(ProductListActivity.EXTRA_NOTICE, getString(R.string.compare_brand_fallback));
        }
        intent.putExtra(ProductListActivity.EXTRA_PICK_MODE, true);
        intent.putExtra(ProductListActivity.EXTRA_EXCLUDE, productA);   // 첫 제품은 목록에서 뺀다
        pickLauncher.launch(intent);
    }

    // ── 최저가 불러오기 ───────────────────────────────────────

    private void loadBestPrice(boolean forA) {
        Product p = forA ? productA : productB;
        Call<BestPriceResponse> call = ApiClient.get().getBestPrice(p.title, category);
        if (forA) callA = call; else callB = call;

        call.enqueue(new Callback<BestPriceResponse>() {
            @Override
            public void onResponse(@NonNull Call<BestPriceResponse> c,
                                   @NonNull Response<BestPriceResponse> response) {
                BestPriceResponse body = response.body();
                // 가격이 2배 넘게 다르면 이름만 비슷한 다른 상품으로 보고 버린다
                if (body != null && body.available && isSimilarPrice(p.price, body.price)) {
                    if (forA) bestA = body; else bestB = body;
                }
                if (productB != null) showComparison();
            }

            @Override
            public void onFailure(@NonNull Call<BestPriceResponse> c, @NonNull Throwable t) {
                // 실패하면 목록에 있던 가격으로 비교한다 (화면은 이미 그 가격으로 그려져 있음)
            }
        });
    }

    // ── 비교 화면 그리기 ──────────────────────────────────────

    private void showComparison() {
        emptyBox.setVisibility(View.GONE);
        resultBox.setVisibility(View.VISIBLE);
        tableBox.setVisibility(View.VISIBLE);
        bindHeader(R.id.compare_b, productB);

        long priceA = currentPrice(productA, bestA);
        long priceB = currentPrice(productB, bestB);

        // 결론: 어느 쪽이 얼마나 싼지
        if (priceA > 0 && priceB > 0 && priceA != priceB) {
            boolean aCheaper = priceA < priceB;
            long diff = Math.abs(priceA - priceB);
            long base = Math.max(priceA, priceB);
            verdict.setText(getString(R.string.compare_verdict,
                    aCheaper ? "A" : "B", won.format(diff), Math.round(diff * 100.0 / base)));
        } else if (priceA > 0 && priceA == priceB) {
            verdict.setText(R.string.compare_same_price);
        } else {
            verdict.setText(R.string.compare_no_price);
        }

        rows.removeAllViews();
        addRow("지금 최저가", priceText(priceA), priceText(priceB), lowerIsBetter(priceA, priceB));
        addRow("최저가 판매처", mallText(productA, bestA), mallText(productB, bestB), 0);
        addRow("브랜드", orDash(productA.brand), orDash(productB.brand), 0);
        addRow("리뷰 수",
                productA.reviewCount > 0 ? won.format(productA.reviewCount) + "개" : "-",
                productB.reviewCount > 0 ? won.format(productB.reviewCount) + "개" : "-",
                higherIsBetter(productA.reviewCount, productB.reviewCount));
        addRow("평점",
                productA.reviewScore > 0 ? "★ " + productA.reviewScore : "-",
                productB.reviewScore > 0 ? "★ " + productB.reviewScore : "-",
                higherIsBetter(productA.reviewScore, productB.reviewScore));

        bindShopButton(R.id.compare_shop_a, productA, bestA);
        bindShopButton(R.id.compare_shop_b, productB, bestB);
    }

    /** 위쪽 제품 카드(이미지 + 이름) */
    private void bindHeader(int containerId, Product p) {
        View box = findViewById(containerId);
        ImageView image = box.findViewById(R.id.cmp_image);
        TextView title = box.findViewById(R.id.cmp_title);
        Glide.with(this).load(p.image).centerInside().into(image);
        title.setText(p.title);
    }

    /**
     * 비교표 한 줄: 항목 이름 / A 값 / B 값.
     * better: -1이면 A가 낫다, 1이면 B가 낫다, 0이면 강조 없음.
     */
    private void addRow(String label, String a, String b, int better) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_compare_row, rows, false);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        TextView va = row.findViewById(R.id.row_a);
        TextView vb = row.findViewById(R.id.row_b);
        va.setText(a);
        vb.setText(b);
        int good = ContextCompat.getColor(this, R.color.score_green);
        if (better < 0) highlight(va, good);
        if (better > 0) highlight(vb, good);
        rows.addView(row);
    }

    private void highlight(TextView v, int color) {
        v.setTextColor(color);
        v.setTypeface(v.getTypeface(), Typeface.BOLD);
    }

    private void bindShopButton(int id, Product p, BestPriceResponse best) {
        Button b = findViewById(id);
        String link = (best != null && best.link != null) ? best.link : p.link;
        b.setVisibility(link != null && !link.isEmpty() ? View.VISIBLE : View.GONE);
        b.setOnClickListener(v -> openLink(link));
    }

    // ── F-13 AI 비교 ─────────────────────────────────────────

    private void onAiCompareClick() {
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(this, "AI 비교는 로그인 후에 쓸 수 있어요", Toast.LENGTH_SHORT).show();
            loginLauncher.launch(new Intent(this, LoginActivity.class));
            return;
        }
        runAiCompare();
    }

    private void runAiCompare() {
        if (productB == null) return;
        if (aiCall != null) aiCall.cancel();

        aiButton.setEnabled(false);
        aiProgress.setVisibility(View.VISIBLE);
        aiState.setVisibility(View.VISIBLE);
        aiState.setText(R.string.ai_compare_loading);
        aiResult.setVisibility(View.GONE);

        aiCall = ApiClient.get().aiCompare(productA.title, productB.title);
        aiCall.enqueue(new Callback<AiCompareResponse>() {
            @Override
            public void onResponse(@NonNull Call<AiCompareResponse> call,
                                   @NonNull Response<AiCompareResponse> response) {
                aiProgress.setVisibility(View.GONE);
                aiButton.setEnabled(true);

                if (response.code() == 401) {
                    // 토큰이 만료됐거나 잘못됨 → 로그아웃 처리 후 다시 로그인하게 한다
                    SessionManager.logout();
                    aiState.setText(R.string.ai_compare_relogin);
                    loginLauncher.launch(new Intent(CompareActivity.this, LoginActivity.class));
                    return;
                }
                AiCompareResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.error != null
                        || body.products == null || body.products.size() < 2) {
                    aiState.setText(R.string.ai_compare_failed);
                    aiButton.setText(R.string.retry);
                    return;
                }
                bindAiResult(body);
            }

            @Override
            public void onFailure(@NonNull Call<AiCompareResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                aiProgress.setVisibility(View.GONE);
                aiButton.setEnabled(true);
                aiButton.setText(R.string.retry);
                aiState.setText(R.string.ai_compare_failed);
            }
        });
    }

    private void bindAiResult(AiCompareResponse r) {
        aiState.setVisibility(View.GONE);
        aiButton.setText(R.string.ai_compare_again);
        aiResult.setVisibility(View.VISIBLE);

        // 결론: 누가 더 나은지 + 이유
        TextView winner = findViewById(R.id.ai_winner);
        if (r.winner != null && (r.winner == 0 || r.winner == 1)) {
            winner.setText(getString(R.string.ai_winner, r.winner == 0 ? "A" : "B"));
        } else {
            winner.setText(R.string.ai_winner_tie);
        }
        setOrHide(findViewById(R.id.ai_winner_reason), r.winnerReason);
        setOrHide(findViewById(R.id.ai_verdict), r.verdict);

        bindAiProduct(findViewById(R.id.ai_product_a), "A", r.products.get(0),
                r.winner != null && r.winner == 0);
        bindAiProduct(findViewById(R.id.ai_product_b), "B", r.products.get(1),
                r.winner != null && r.winner == 1);
    }

    /** 제품 하나의 AI 분석 카드 (item_ai_compare_product.xml) */
    private void bindAiProduct(View card, String label, AiCompareResponse.ProductResult p, boolean isWinner) {
        TextView title = card.findViewById(R.id.aicp_title);
        String score = (p.score != null && p.score > 0)
                ? String.format(Locale.KOREA, "  ★ %.1f", p.score) : "";
        title.setText(label + score + (isWinner ? "  · 추천" : ""));
        if (isWinner) highlight(title, ContextCompat.getColor(this, R.color.score_green));

        setOrHide(card.findViewById(R.id.aicp_summary), p.summary);
        setOrHide(card.findViewById(R.id.aicp_pros), bullets("장점", p.pros));
        setOrHide(card.findViewById(R.id.aicp_cons), bullets("단점", p.cons));
    }

    private void resetAi() {
        if (aiCall != null) aiCall.cancel();
        aiResult.setVisibility(View.GONE);
        aiProgress.setVisibility(View.GONE);
        aiState.setVisibility(View.GONE);
        aiButton.setEnabled(true);
        aiButton.setText(R.string.ai_compare_button);
        // 이전 결과에서 굵게 · 초록으로 바뀐 글자를 원래대로
        resetTitle(findViewById(R.id.ai_product_a));
        resetTitle(findViewById(R.id.ai_product_b));
    }

    private void resetTitle(View card) {
        TextView t = card.findViewById(R.id.aicp_title);
        t.setTextColor(aiState.getTextColors());
        t.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private String bullets(String label, List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(label);
        for (String line : lines) sb.append("\n• ").append(line);
        return sb.toString();
    }

    private void setOrHide(TextView v, String text) {
        if (text == null || text.trim().isEmpty()) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setText(text.trim());
        }
    }

    // ── 도우미 ───────────────────────────────────────────────

    private long currentPrice(Product p, BestPriceResponse best) {
        return best != null ? best.price : p.price;
    }

    private String priceText(long price) {
        return price > 0 ? won.format(price) + "원" : "-";
    }

    private String mallText(Product p, BestPriceResponse best) {
        if (best != null && best.mallName != null && !best.mallName.isEmpty()) return best.mallName;
        return orDash(p.mallName);
    }

    private String orDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private int lowerIsBetter(long a, long b) {
        if (a <= 0 || b <= 0 || a == b) return 0;
        return a < b ? -1 : 1;
    }

    private int higherIsBetter(double a, double b) {
        if (a <= 0 || b <= 0 || a == b) return 0;
        return a > b ? -1 : 1;
    }

    private boolean isSimilarPrice(long listed, long found) {
        if (listed <= 0 || found <= 0) return found > 0;
        double ratio = (double) found / listed;
        return ratio >= 0.5 && ratio <= 2.0;
    }

    private boolean isSameProduct(Product a, Product b) {
        return Product.isSame(a, b);
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
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (categoryCall != null) categoryCall.cancel();
        if (callA != null) callA.cancel();
        if (callB != null) callB.cancel();
        if (aiCall != null) aiCall.cancel();
        super.onDestroy();
    }
}
