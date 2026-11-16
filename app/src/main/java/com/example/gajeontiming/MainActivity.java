package com.example.gajeontiming;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.gajeontiming.adapter.RecentAdapter;
import com.example.gajeontiming.adapter.TrendAdapter;
import com.example.gajeontiming.data.RecentProductDb;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.TrendItem;
import com.example.gajeontiming.model.TrendResponse;
import com.example.gajeontiming.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 홈 화면.
 * F-02 트렌드 TOP10 · F-03 카테고리 선택 · 검색창 → 제품 목록 화면으로 이동
 */
public class MainActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipe;
    private RecyclerView list;
    private ProgressBar progress;
    private TextView stateText;
    private Button retryButton;
    private EditText searchInput;
    private TrendAdapter adapter;
    private TextView recentTitle;
    private RecyclerView recentList;
    private RecentAdapter recentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        swipe = findViewById(R.id.home_swipe);
        list = findViewById(R.id.home_trend_list);
        progress = findViewById(R.id.home_progress);
        stateText = findViewById(R.id.home_state_text);
        retryButton = findViewById(R.id.home_retry);
        searchInput = findViewById(R.id.home_search_input);
        ImageButton searchButton = findViewById(R.id.home_search_button);

        // 검색: 키보드의 검색 키 또는 돋보기 버튼
        searchButton.setOnClickListener(v -> search());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search();
                return true;
            }
            return false;
        });

        setupCategories();
        setupRecent();

        adapter = new TrendAdapter(this::onTrendClick);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        swipe.setOnRefreshListener(this::loadTrend);   // 아래로 당겨서 새로고침
        retryButton.setOnClickListener(v -> loadTrend());

        loadTrend();
    }

    // ── 오른쪽 위 메뉴: 마이페이지 / 가격 알림 ─────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_recommend) {
            startActivity(new Intent(this, RecommendActivity.class));
            return true;
        }
        if (id == R.id.menu_mypage) {
            startActivity(new Intent(this, MyPageActivity.class));
            return true;
        }
        if (id == R.id.menu_alerts) {          // 로그인 안 했으면 알림 화면이 로그인부터 띄운다
            startActivity(new Intent(this, AlertListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 리포트를 보고 돌아오면 최근 본 제품을 새로 그린다 */
    @Override
    protected void onResume() {
        super.onResume();
        bindRecent();
    }

    // ── F-08 최근 본 제품 ─────────────────────────────────────

    private void setupRecent() {
        recentTitle = findViewById(R.id.home_recent_title);
        recentList = findViewById(R.id.home_recent_list);
        recentAdapter = new RecentAdapter(true, entry -> {
            Intent intent = new Intent(this, ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, entry.product);
            intent.putExtra(ProductDetailActivity.EXTRA_CATEGORY, entry.category);
            startActivity(intent);
        });
        recentList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recentList.setAdapter(recentAdapter);
    }

    private void bindRecent() {
        List<RecentProductDb.Entry> entries = RecentProductDb.get(this).list(10);
        recentAdapter.setItems(entries);
        int vis = entries.isEmpty() ? View.GONE : View.VISIBLE;
        recentTitle.setVisibility(vis);
        recentList.setVisibility(vis);
    }

    // ── F-03 카테고리 ─────────────────────────────────────────

    private void setupCategories() {
        ChipGroup group = findViewById(R.id.home_category_group);
        for (String name : Categories.ALL) {
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setOnClickListener(v -> openProductList(name, name));
            group.addView(chip);
        }
    }

    // ── 검색 ────────────────────────────────────────────────

    private void search() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "검색어를 입력해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        // 검색어에 품목 이름이 들어 있으면 그 품목 규칙으로 걸러서 정확도를 높인다
        openProductList(query, Categories.detect(query));
    }

    private void openProductList(String query, String category) {
        Intent intent = new Intent(this, ProductListActivity.class);
        intent.putExtra(ProductListActivity.EXTRA_QUERY, query);
        intent.putExtra(ProductListActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    // ── F-02 트렌드 ─────────────────────────────────────────

    private void loadTrend() {
        if (!swipe.isRefreshing()) {
            showLoading();
        }

        ApiClient.get().getTrend(null).enqueue(new Callback<TrendResponse>() {
            @Override
            public void onResponse(@NonNull Call<TrendResponse> call,
                                   @NonNull Response<TrendResponse> response) {
                swipe.setRefreshing(false);
                TrendResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    showError(getString(R.string.home_trend_failed));
                } else if (body.items == null || body.items.isEmpty()) {
                    showError(getString(R.string.home_trend_empty));
                } else {
                    adapter.setItems(body.items);
                    showList();
                }
            }

            @Override
            public void onFailure(@NonNull Call<TrendResponse> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                showError(getString(R.string.home_trend_failed));
            }
        });
    }

    /** 트렌드 제품을 누르면 바로 리포트 화면으로 (TrendItem도 Product라서 그대로 넘길 수 있다) */
    private void onTrendClick(TrendItem item) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, item);
        startActivity(intent);
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.home_trend_loading);
        retryButton.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
    }

    private void showList() {
        progress.setVisibility(View.GONE);
        stateText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
    }

    private void showError(String text) {
        progress.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
        retryButton.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
    }
}
