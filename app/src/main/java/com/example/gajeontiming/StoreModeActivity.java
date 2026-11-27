package com.example.gajeontiming;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.gajeontiming.alert.AlertRegistration;
import com.example.gajeontiming.judge.TimingJudge;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.CategoryResponse;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.model.ProductListResponse;
import com.example.gajeontiming.model.TimingResponse;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.scan.AccessoryFilter;
import com.example.gajeontiming.scan.ModelNameExtractor;
import com.example.gajeontiming.scan.SearchSuggester;
import com.example.gajeontiming.store.ProductMatcher;
import com.example.gajeontiming.store.SearchPlan;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 확장 기능: 매장 모드.
 * 가격표 사진 한 장으로 모델명 인식 → 검색 → 가장 맞는 본품 자동 선택 → 구매 타이밍 → 판단 카드까지
 * 한 화면에서 끝낸다. (모델명 스캔 F-16은 제품 목록까지, 이 화면은 "사도 되는지"까지)
 *
 * 다른 화면에서 EXTRA_QUERY(모델명)를 넘기면 사진 단계를 건너뛰고 바로 검색부터 한다.
 *
 * 재사용: ModelNameExtractor(모델명 판별) · SearchPlan(단계적 재검색) · ProductMatcher(본품 고르기)
 *        · AccessoryFilter(소모품) · SearchSuggester(못 찾았을 때 제안) · TimingJudge(가전무쌍 점수)
 */
public class StoreModeActivity extends AppCompatActivity {

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_CATEGORY = "category";

    private static final int SEARCH_SIZE = 20;
    private static final int MAX_CANDIDATES = 3;

    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);
    private final AlertRegistration alertRegistration = new AlertRegistration(this);

    // 화면 요소
    private ImageView photo;
    private View captureBox, resultCard, notFoundBox, candidateBox;
    private ProgressBar progress;
    private TextView stateText, modelLabel;
    private ChipGroup modelChips, candidateChips, suggestChips;

    // 진행 상태
    private Uri photoUri;
    private TextRecognizer recognizer;
    private String model;                  // 지금 찾는 모델명
    private String category;               // 사진 · 검색어 · 서버에서 알아낸 품목
    private List<SearchPlan.Attempt> plan = new ArrayList<>();
    private int attemptIndex;
    private final List<String> removedTitles = new ArrayList<>();
    private List<ProductMatcher.Match> candidates = new ArrayList<>();
    private Product selected;
    private TimingResponse.Analysis lastAnalysis;
    private final List<Call<?>> calls = new ArrayList<>();

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            saved -> {
                if (Boolean.TRUE.equals(saved)) recognize(photoUri);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) recognize(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_mode);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.store_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        photo = findViewById(R.id.store_photo);
        captureBox = findViewById(R.id.store_capture);
        resultCard = findViewById(R.id.store_result);
        notFoundBox = findViewById(R.id.store_not_found);
        candidateBox = findViewById(R.id.store_candidate_box);
        progress = findViewById(R.id.store_progress);
        stateText = findViewById(R.id.store_state);
        modelLabel = findViewById(R.id.store_model_label);
        modelChips = findViewById(R.id.store_model_chips);
        candidateChips = findViewById(R.id.store_candidate_chips);
        suggestChips = findViewById(R.id.store_suggest_chips);

        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

        findViewById(R.id.store_camera).setOnClickListener(v -> openCamera());
        findViewById(R.id.store_gallery).setOnClickListener(v -> pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        findViewById(R.id.store_retake).setOnClickListener(v -> resetToCapture());
        findViewById(R.id.store_retake_nf).setOnClickListener(v -> resetToCapture());
        findViewById(R.id.store_detail).setOnClickListener(v -> openDetail());
        findViewById(R.id.store_alert).setOnClickListener(v -> {
            if (selected != null && lastAnalysis != null) {
                alertRegistration.start(selected.title, selected.link, lastAnalysis.curMin, lastAnalysis.histMin);
            }
        });
        findViewById(R.id.store_danawa).setOnClickListener(v -> openDanawa());

        resetToCapture();

        // 스캔 화면 등에서 모델명을 넘겨받았으면 사진 단계를 건너뛴다
        String preset = getIntent().getStringExtra(EXTRA_QUERY);
        if (preset != null && !preset.trim().isEmpty()) {
            String cat = getIntent().getStringExtra(EXTRA_CATEGORY);
            showModelChips(java.util.Collections.singletonList(preset.trim()));
            startSearch(preset.trim(), cat != null ? cat : Categories.detect(preset));
        }
    }

    // ── 1. 사진 → 모델명 ─────────────────────────────────────

    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "scan");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("폴더를 만들 수 없음");
            File file = File.createTempFile("store_", ".jpg", dir);
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            takePicture.launch(photoUri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.store_camera_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void recognize(Uri uri) {
        photo.setImageURI(uri);
        photo.setVisibility(View.VISIBLE);
        showWorking(getString(R.string.store_reading));

        InputImage image;
        try {
            image = InputImage.fromFilePath(this, uri);
        } catch (IOException e) {
            showMessage(getString(R.string.store_read_failed));
            return;
        }
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    ModelNameExtractor.Result r = ModelNameExtractor.extract(text.getText());
                    if (r.candidates.isEmpty()) {
                        showMessage(getString(R.string.store_no_model));
                        return;
                    }
                    showModelChips(r.candidates);
                    startSearch(r.candidates.get(0), r.category);
                })
                .addOnFailureListener(e -> showMessage(getString(R.string.store_read_failed)));
    }

    /** 사진에서 읽은 모델명 후보. 누르면 그 모델명으로 다시 찾는다 */
    private void showModelChips(List<String> names) {
        modelChips.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String name : names) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, modelChips, false);
            chip.setText(name);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> startSearch(name, category != null ? category : Categories.detect(name)));
            modelChips.addView(chip);
        }
        modelLabel.setVisibility(View.VISIBLE);
        modelChips.setVisibility(View.VISIBLE);
    }

    // ── 2. 검색 → 본품 고르기 ─────────────────────────────────

    private void startSearch(String query, String cat) {
        cancelCalls();
        model = query;
        category = cat;
        plan = SearchPlan.build(query, cat);
        attemptIndex = 0;
        removedTitles.clear();
        captureBox.setVisibility(View.GONE);
        showWorking(getString(R.string.store_searching, query));
        runAttempt();
    }

    private void runAttempt() {
        if (attemptIndex >= plan.size()) {
            showNotFound();
            return;
        }
        SearchPlan.Attempt a = plan.get(attemptIndex);
        Call<ProductListResponse> call = ApiClient.get().searchProducts(a.query, 1, SEARCH_SIZE, "sim", a.category);
        calls.add(call);
        call.enqueue(new Callback<ProductListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> c, @NonNull Response<ProductListResponse> res) {
                ProductListResponse body = res.body();
                List<Product> items = body != null && body.items != null ? body.items : new ArrayList<>();
                for (Product p : items) {
                    if (p != null && AccessoryFilter.isAccessory(p.title, p.price)) removedTitles.add(p.title);
                }
                List<ProductMatcher.Match> ranked = ProductMatcher.rank(items, model, category, MAX_CANDIDATES);
                if (ranked.isEmpty()) {
                    attemptIndex++;            // 이 검색으로는 본품이 없으면 다음 방법으로 넓힌다
                    runAttempt();
                    return;
                }
                candidates = ranked;
                showCandidateChips();
                choose(ranked.get(0));
            }

            @Override
            public void onFailure(@NonNull Call<ProductListResponse> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                showMessage(getString(R.string.store_network));
            }
        });
    }

    /** 다른 후보로 바꿀 수 있게 칩으로 보여준다 */
    private void showCandidateChips() {
        candidateChips.removeAllViews();
        if (candidates.size() < 2) {
            candidateBox.setVisibility(View.GONE);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ProductMatcher.Match m : candidates) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, candidateChips, false);
            chip.setText(shortTitle(m.product.title));
            chip.setOnClickListener(v -> {
                if (m.product != selected) choose(m);
            });
            candidateChips.addView(chip);
        }
        candidateBox.setVisibility(View.VISIBLE);
    }

    // ── 3. 구매 타이밍 → 판단 카드 ────────────────────────────

    private void choose(ProductMatcher.Match match) {
        selected = match.product;
        lastAnalysis = null;
        markSelectedChip();
        showWorking(getString(R.string.store_judging));

        // 품목을 모르면 서버에 상품명 → 품목을 물어본다 (소모품 차단 · 품목 평균 비교 정확도)
        if (category != null) {
            loadTiming(match);
            return;
        }
        Call<CategoryResponse> call = ApiClient.get().getCategory(selected.title);
        calls.add(call);
        call.enqueue(new Callback<CategoryResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryResponse> c, @NonNull Response<CategoryResponse> res) {
                if (res.body() != null && res.body().category != null) category = res.body().category;
                loadTiming(match);
            }

            @Override
            public void onFailure(@NonNull Call<CategoryResponse> c, @NonNull Throwable t) {
                if (!c.isCanceled()) loadTiming(match);
            }
        });
    }

    private void loadTiming(ProductMatcher.Match match) {
        Product p = match.product;
        // 제품명에 품목 이름이 없으면 붙여서 서버가 소모품을 거르게 한다 (구매 타이밍 화면과 같은 규칙)
        String query = (category != null && !p.title.contains(category)) ? p.title + " " + category : p.title;
        Call<TimingResponse> call = ApiClient.get().getTiming(query, p.id, p.price);
        calls.add(call);
        call.enqueue(new Callback<TimingResponse>() {
            @Override
            public void onResponse(@NonNull Call<TimingResponse> c, @NonNull Response<TimingResponse> res) {
                TimingResponse body = res.body();
                if (body == null || body.analysis == null) {
                    bindCard(match, null, getString(R.string.store_no_price_data));
                    return;
                }
                long tracked = body.analysis.curMin;
                if (p.price > 0 && tracked > 0) {
                    double ratio = (double) tracked / p.price;
                    if (ratio < 0.5 || ratio > 2.0) {       // 이름만 비슷한 다른 상품이면 판단하지 않는다
                        bindCard(match, null, getString(R.string.store_price_mismatch));
                        return;
                    }
                }
                bindCard(match, body, null);
            }

            @Override
            public void onFailure(@NonNull Call<TimingResponse> c, @NonNull Throwable t) {
                if (!c.isCanceled()) bindCard(match, null, getString(R.string.store_network));
            }
        });
    }

    private void bindCard(ProductMatcher.Match match, TimingResponse body, String problem) {
        progress.setVisibility(View.GONE);
        stateText.setVisibility(View.GONE);
        notFoundBox.setVisibility(View.GONE);
        resultCard.setVisibility(View.VISIBLE);

        Product p = match.product;
        Glide.with(this).load(p.image).centerInside().into((ImageView) findViewById(R.id.store_image));
        ((TextView) findViewById(R.id.store_name)).setText(p.title);

        TextView exactNote = findViewById(R.id.store_exact_note);
        exactNote.setVisibility(match.isExact() ? View.GONE : View.VISIBLE);
        exactNote.setText(getString(R.string.store_not_exact, model));

        TextView price = findViewById(R.id.store_price);
        TextView badge = findViewById(R.id.store_badge);
        TextView message = findViewById(R.id.store_message);
        View judgeBox = findViewById(R.id.store_judge_box);
        View alertButton = findViewById(R.id.store_alert);

        if (body == null) {                              // 판단할 수 없는 경우: 이유만 보여준다
            lastAnalysis = null;
            price.setText(p.price > 0 ? won.format(p.price) + "원" : "");
            badge.setVisibility(View.GONE);
            message.setText(problem);
            judgeBox.setVisibility(View.GONE);
            alertButton.setVisibility(View.GONE);
            return;
        }

        TimingResponse.Analysis a = body.analysis;
        lastAnalysis = a;
        price.setText(won.format(a.curMin) + "원");
        badge.setVisibility(View.VISIBLE);
        badge.setText(a.score);
        badge.getBackground().mutate().setTint(colorFor(a.color));
        message.setText(a.message);
        alertButton.setVisibility(View.VISIBLE);

        // 가전무쌍 점수 (근거는 영향이 큰 3개만)
        long[] prices = new long[body.data == null ? 0 : body.data.size()];
        for (int i = 0; i < prices.length; i++) prices[i] = body.data.get(i).price;
        TimingJudge.Result r = TimingJudge.judge(prices, a.curMin, a.launchDiscPct,
                a.searchSummary != null ? a.searchSummary.diffPct : null);
        if (r == null) {
            judgeBox.setVisibility(View.GONE);
            return;
        }
        judgeBox.setVisibility(View.VISIBLE);
        int c = ContextCompat.getColor(this, r.score >= 75 ? R.color.score_green
                : r.score >= 55 ? R.color.score_blue
                : r.score >= 40 ? R.color.score_yellow : R.color.score_red);
        TextView score = findViewById(R.id.store_score);
        score.setText(getString(R.string.judge_score, r.score));
        score.setTextColor(c);
        TextView grade = findViewById(R.id.store_grade);
        grade.setText(r.grade + (r.reliable ? "" : " · " + getString(R.string.store_reference_only)));
        grade.setTextColor(c);

        LinearLayout reasons = findViewById(R.id.store_reasons);
        reasons.removeAllViews();
        for (int i = 0; i < Math.min(3, r.reasons.size()); i++) {
            TimingJudge.Reason reason = r.reasons.get(i);
            TextView row = new TextView(this);
            String sign = reason.points > 0 ? "+" + reason.points : reason.points < 0 ? String.valueOf(reason.points) : "·";
            row.setText(sign + "  " + reason.text);
            row.setTextSize(14);
            row.setPadding(0, 3, 0, 3);
            reasons.addView(row);
        }
    }

    // ── 못 찾았을 때 ─────────────────────────────────────────

    private void showNotFound() {
        progress.setVisibility(View.GONE);
        resultCard.setVisibility(View.GONE);
        candidateBox.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(removedTitles.isEmpty()
                ? getString(R.string.store_not_found, model)
                : getString(R.string.store_only_accessories, model));
        notFoundBox.setVisibility(View.VISIBLE);

        suggestChips.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String s : SearchSuggester.suggest(model, category, removedTitles)) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, suggestChips, false);
            chip.setText(s);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> startSearch(s, Categories.detect(s) != null ? Categories.detect(s) : category));
            suggestChips.addView(chip);
        }
    }

    // ── 화면 상태 · 이동 ─────────────────────────────────────

    private void resetToCapture() {
        cancelCalls();
        selected = null;
        lastAnalysis = null;
        photo.setVisibility(View.GONE);
        captureBox.setVisibility(View.VISIBLE);
        resultCard.setVisibility(View.GONE);
        notFoundBox.setVisibility(View.GONE);
        candidateBox.setVisibility(View.GONE);
        modelLabel.setVisibility(View.GONE);
        modelChips.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        stateText.setVisibility(View.GONE);
    }

    private void showWorking(String text) {
        resultCard.setVisibility(View.GONE);
        notFoundBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
    }

    private void showMessage(String text) {
        progress.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
        captureBox.setVisibility(View.VISIBLE);   // 다시 찍을 수 있게
    }

    private void markSelectedChip() {
        for (int i = 0; i < candidateChips.getChildCount(); i++) {
            Chip chip = (Chip) candidateChips.getChildAt(i);
            chip.setChecked(i < candidates.size() && candidates.get(i).product == selected);
        }
    }

    private void openDetail() {
        if (selected == null) return;
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, selected);
        intent.putExtra(ProductDetailActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
    }

    private void openDanawa() {
        try {
            Uri uri = Uri.parse("https://search.danawa.com/dsearch.php?query=" + Uri.encode(model));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.store_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private String shortTitle(String title) {
        return title.length() > 24 ? title.substring(0, 24) + "…" : title;
    }

    private int colorFor(String color) {
        int id = "green".equals(color) ? R.color.score_green
                : "blue".equals(color) ? R.color.score_blue
                : "yellow".equals(color) ? R.color.score_yellow : R.color.score_red;
        return ContextCompat.getColor(this, id);
    }

    private void cancelCalls() {
        for (Call<?> c : calls) c.cancel();
        calls.clear();
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
        cancelCalls();
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }
}
