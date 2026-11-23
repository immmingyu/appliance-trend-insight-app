package com.example.gajeontiming.b2b;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.LoginActivity;
import com.example.gajeontiming.R;
import com.example.gajeontiming.adapter.ProductAdapter;
import com.example.gajeontiming.model.MarketModels;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.AuthGuard;
import com.example.gajeontiming.ui.Pulse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * B2B 제품 시장 분석 1/2 (단계 4, 승인된 사업자 회원 전용).
 * GET /api/b2b/product-search?q= 로 제품을 찾고, 고르면 MarketAnalysisActivity로 분석한다.
 * 정확한 제품명에는 카테고리 단어가 없는 경우가 많아, 사용자가 입력한 검색어를 hint로 같이 넘긴다.
 */
public class MarketSearchActivity extends AppCompatActivity {

    private EditText input;
    private View skeleton;
    private TextView stateText;
    private ProductAdapter adapter;
    private Call<MarketModels.ProductSearch> call;
    private String lastQuery;
    private RecyclerView list;
    private View sortScroll;
    private ChipGroup sortGroup;
    /** 서버가 준 순서 그대로 (추천순). 정렬은 이걸 복사해서 한다 */
    private final List<MarketModels.SearchItem> results = new ArrayList<>();
    private MarketSort.Order order = MarketSort.Order.RECOMMENDED;

    /** 칩 id ↔ 정렬 */
    private static final int[] SORT_CHIPS = {
            R.id.market_sort_recommended, R.id.market_sort_popular, R.id.market_sort_latest,
            R.id.market_sort_low, R.id.market_sort_high, R.id.market_sort_energy};
    private static final MarketSort.Order[] SORT_ORDERS = {
            MarketSort.Order.RECOMMENDED, MarketSort.Order.POPULAR, MarketSort.Order.LATEST,
            MarketSort.Order.PRICE_LOW, MarketSort.Order.PRICE_HIGH, MarketSort.Order.ENERGY};

    /** 로그인이 만료됐으면 로그인 후 같은 검색을 다시 한다 */
    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && lastQuery != null) search(lastQuery);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market_search);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.market_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        input = findViewById(R.id.market_search);
        skeleton = findViewById(R.id.market_skeleton);
        stateText = findViewById(R.id.market_state);

        list = findViewById(R.id.market_list);
        sortScroll = findViewById(R.id.market_sort_scroll);
        sortGroup = findViewById(R.id.market_sort_group);
        sortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            for (int i = 0; i < SORT_CHIPS.length; i++) {
                if (SORT_CHIPS[i] == checkedIds.get(0) && SORT_ORDERS[i] != order) {
                    order = SORT_ORDERS[i];
                    showResults();
                }
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(product ->
                MarketAnalysisActivity.start(this, product.title, lastQuery));
        list.setAdapter(adapter);

        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                submit();
                return true;
            }
            return false;
        });
        TextInputLayout layout = findViewById(R.id.market_search_layout);
        layout.setEndIconContentDescription(R.string.market_search_button);
        layout.setEndIconOnClickListener(v -> submit());
    }

    private void submit() {
        String q = input.getText() == null ? "" : input.getText().toString().trim();
        if (q.isEmpty()) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        search(q);
    }

    private void search(String q) {
        lastQuery = q;
        if (call != null) call.cancel();
        adapter.clear();
        results.clear();
        sortScroll.setVisibility(View.GONE);
        stateText.setVisibility(View.GONE);
        setSkeleton(true);

        call = ApiClient.get().searchMarketProducts(q);
        call.enqueue(new Callback<MarketModels.ProductSearch>() {
            @Override
            public void onResponse(@NonNull Call<MarketModels.ProductSearch> c,
                                   @NonNull Response<MarketModels.ProductSearch> response) {
                setSkeleton(false);
                if (AuthGuard.isLoggedOut(response)) {
                    loginLauncher.launch(new Intent(MarketSearchActivity.this, LoginActivity.class));
                    return;
                }
                MarketModels.ProductSearch body = response.body();
                if (!response.isSuccessful() || body == null) {
                    showState(AuthGuard.message(response, getString(R.string.market_search_failed)));
                    return;
                }
                if (body.items != null) {
                    for (MarketModels.SearchItem it : body.items) {
                        if (it.title != null && !it.title.isEmpty()) results.add(it);
                    }
                }
                if (results.isEmpty()) {
                    showState(getString(R.string.market_search_empty, q));
                    return;
                }
                bindSortChips();
                showResults();
            }

            @Override
            public void onFailure(@NonNull Call<MarketModels.ProductSearch> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                setSkeleton(false);
                showState(getString(R.string.market_search_failed));
            }
        });
    }

    /** 새 검색마다: 추천순으로 돌리고, 결과에 값이 하나도 없는 정렬 칩은 숨긴다 */
    private void bindSortChips() {
        order = MarketSort.Order.RECOMMENDED;
        for (int i = 0; i < SORT_CHIPS.length; i++) {
            findViewById(SORT_CHIPS[i]).setVisibility(
                    MarketSort.available(results, SORT_ORDERS[i]) ? View.VISIBLE : View.GONE);
        }
        sortGroup.check(R.id.market_sort_recommended);
        sortScroll.setVisibility(View.VISIBLE);
    }

    private void showResults() {
        adapter.clear();
        adapter.addItems(toProducts(MarketSort.sort(results, order)));
        list.scrollToPosition(0);
    }

    /**
     * 제품 목록 어댑터(ProductAdapter)를 그대로 쓰려고 Product로 바꾼다.
     * 판매처 칸에 출시년월 · 에너지 정보, 리뷰 칸에 다나와 리뷰 수 · 평점을 넣는다.
     */
    private List<Product> toProducts(List<MarketModels.SearchItem> items) {
        List<Product> out = new ArrayList<>();
        for (MarketModels.SearchItem it : items) {
            Product p = new Product();
            p.title = it.title;
            p.brand = it.brand;
            p.price = it.price == null ? 0 : (int) Math.round(it.price);
            p.image = it.image;
            p.link = it.link;
            String energy = AnalysisFormat.energy(it.energyGrade, it.monthlyCost, it.monthlyCostBasis);
            String release = it.release != null && !it.release.trim().isEmpty()
                    ? getString(R.string.market_release, it.release.trim().replace('-', '.')) : null;
            p.mallName = release == null ? energy : energy == null ? release : release + " · " + energy;
            Double reviews = MarketSort.number(it.reviewCount);
            Double score = MarketSort.number(it.reviewScore);
            p.reviewCount = reviews == null ? 0 : (int) Math.round(reviews);
            p.reviewScore = score == null ? 0 : score;
            out.add(p);
        }
        return out;
    }

    private void showState(String text) {
        stateText.setText(text);
        stateText.setVisibility(View.VISIBLE);
    }

    private void setSkeleton(boolean show) {
        skeleton.setVisibility(show ? View.VISIBLE : View.GONE);
        ViewGroup group = (ViewGroup) skeleton;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (show) Pulse.start(group.getChildAt(i));
            else Pulse.stop(group.getChildAt(i));
        }
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
