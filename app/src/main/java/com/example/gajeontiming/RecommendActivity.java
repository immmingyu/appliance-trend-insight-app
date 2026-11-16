package com.example.gajeontiming;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.adapter.RecommendAdapter;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.RecommendResponse;
import com.example.gajeontiming.model.RecommendResponse.RecommendItem;
import com.example.gajeontiming.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-14 AI 추천 화면.
 * 품목(필수) · 예산 · 사용 환경 · 추가 조건을 고르면 앱이 "에어컨 1인 가구 원룸 30만원 이하" 같은
 * 문장을 만들어 서버에 보내고, AI가 고른 제품 3개를 이유와 함께 보여준다.
 */
public class RecommendActivity extends AppCompatActivity {

    /** 사용 환경 · 선호 조건 (여러 개 선택 가능) */
    private static final String[] CONDITIONS = {
            "1인 가구", "2~3인 가족", "4인 이상 가족", "원룸", "거실",
            "조용한 제품", "에너지 효율", "가성비", "프리미엄", "설치 간편"
    };

    private ChipGroup categoryGroup, conditionGroup;
    private EditText budgetInput, extraInput;
    private TextView preview, stateText, resultTitle, limitedNote;
    private Button submitButton;
    private ProgressBar progress;
    private RecyclerView resultList;
    private NestedScrollView scroll;
    private RecommendAdapter adapter;

    private String lastCategory;     // 결과 제품을 눌렀을 때 리포트로 넘길 품목
    private Call<RecommendResponse> call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.recommend_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        categoryGroup = findViewById(R.id.rec_category_group);
        conditionGroup = findViewById(R.id.rec_condition_group);
        budgetInput = findViewById(R.id.rec_budget);
        extraInput = findViewById(R.id.rec_extra);
        preview = findViewById(R.id.rec_preview);
        submitButton = findViewById(R.id.rec_submit);
        progress = findViewById(R.id.rec_progress);
        stateText = findViewById(R.id.rec_state);
        resultTitle = findViewById(R.id.rec_result_title);
        limitedNote = findViewById(R.id.rec_limited);
        resultList = findViewById(R.id.rec_list);
        scroll = findViewById(R.id.rec_scroll);

        addChips(categoryGroup, Categories.ALL);    // 품목: 하나만 선택
        addChips(conditionGroup, CONDITIONS);       // 조건: 여러 개 선택

        // 무엇을 고르든 "이렇게 물어볼게요" 미리보기를 바로 바꾼다
        categoryGroup.setOnCheckedStateChangeListener((g, ids) -> updatePreview());
        conditionGroup.setOnCheckedStateChangeListener((g, ids) -> updatePreview());
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        };
        budgetInput.addTextChangedListener(watcher);
        extraInput.addTextChangedListener(watcher);

        adapter = new RecommendAdapter(this::openDetail);
        resultList.setLayoutManager(new LinearLayoutManager(this));
        resultList.setNestedScrollingEnabled(false);
        resultList.setAdapter(adapter);

        submitButton.setOnClickListener(v -> submit());
        updatePreview();
    }

    /** 선택형 칩(item_filter_chip.xml)을 이름 수만큼 만들어 붙인다. id는 ChipGroup이 자동으로 정한다 */
    private void addChips(ChipGroup group, String[] names) {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String name : names) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, group, false);
            chip.setText(name);
            group.addView(chip);
        }
    }

    // ── 문장 만들기 ───────────────────────────────────────────

    private String selectedCategory() {
        List<Integer> ids = categoryGroup.getCheckedChipIds();
        if (ids.isEmpty()) return null;
        Chip chip = findViewById(ids.get(0));
        return chip.getText().toString();
    }

    /** 고른 값으로 서버에 보낼 문장을 만든다. 품목을 안 골랐으면 null */
    private String buildSentence() {
        String category = selectedCategory();
        if (category == null) return null;

        List<String> parts = new ArrayList<>();
        parts.add(category);
        for (int id : conditionGroup.getCheckedChipIds()) {
            Chip chip = findViewById(id);
            parts.add(chip.getText().toString());
        }
        String extra = extraInput.getText().toString().trim();
        if (!extra.isEmpty()) parts.add(extra);

        // 서버는 "숫자+만원" 형식으로 예산을 알아듣는다
        String budget = budgetInput.getText().toString().trim();
        if (!budget.isEmpty() && !budget.equals("0")) parts.add(budget + "만원 이하");

        return String.join(" ", parts);
    }

    private void updatePreview() {
        String sentence = buildSentence();
        if (sentence == null) {
            preview.setText(R.string.recommend_preview_empty);
            submitButton.setEnabled(false);
        } else {
            preview.setText(getString(R.string.recommend_preview, sentence));
            submitButton.setEnabled(call == null);
        }
    }

    // ── 추천 받기 ─────────────────────────────────────────────

    private void submit() {
        String sentence = buildSentence();
        if (sentence == null) return;
        lastCategory = selectedCategory();
        hideKeyboard();

        submitButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.recommend_loading);
        resultTitle.setVisibility(View.GONE);
        limitedNote.setVisibility(View.GONE);
        adapter.setItems(null);
        scrollToResult();

        call = ApiClient.get().recommend(sentence);
        call.enqueue(new Callback<RecommendResponse>() {
            @Override
            public void onResponse(@NonNull Call<RecommendResponse> c,
                                   @NonNull Response<RecommendResponse> response) {
                call = null;
                progress.setVisibility(View.GONE);
                submitButton.setEnabled(true);

                RecommendResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    stateText.setText(R.string.recommend_failed);
                    return;
                }
                if (body.recommendations == null || body.recommendations.isEmpty()) {
                    // 예: "'벽걸이 에어컨'의 최저가는 약 31만원이에요. 예산을 36만원 이상으로 올려보세요!"
                    stateText.setText(body.error != null ? body.error : getString(R.string.recommend_empty));
                    return;
                }
                stateText.setVisibility(View.GONE);
                resultTitle.setVisibility(View.VISIBLE);
                resultTitle.setText(body.searchTerm != null
                        ? getString(R.string.recommend_result_with_term, body.searchTerm)
                        : getString(R.string.recommend_result));
                limitedNote.setVisibility(body.aiLimited ? View.VISIBLE : View.GONE);
                adapter.setItems(body.recommendations);
                scrollToResult();
            }

            @Override
            public void onFailure(@NonNull Call<RecommendResponse> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                call = null;
                progress.setVisibility(View.GONE);
                submitButton.setEnabled(true);
                stateText.setText(R.string.recommend_network);
            }
        });
    }

    /** 결과를 누르면 리포트로. 고른 품목을 같이 넘겨 구매 타이밍 · 비교 정확도를 지킨다 */
    private void openDetail(RecommendItem item) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, item);
        intent.putExtra(ProductDetailActivity.EXTRA_CATEGORY, lastCategory);
        startActivity(intent);
    }

    private void scrollToResult() {
        scroll.post(() -> scroll.smoothScrollTo(0, submitButton.getBottom()));
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
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
        if (call != null) call.cancel();
        super.onDestroy();
    }
}
