package com.example.gajeontiming;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.example.gajeontiming.data.TrendCache;
import com.example.gajeontiming.ui.Pulse;
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

    private static final long SLOW_NOTICE_MS = 5000;   // 5초가 넘으면 "서버 깨우는 중" 안내로 바꿈

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SwipeRefreshLayout swipe;
    private RecyclerView list;
    private View skeleton;   // 트렌드를 불러오는 동안 보여 주는 빈 카드들
    private TextView stateText;
    private Button retryButton;
    private EditText searchInput;
    private TrendAdapter adapter;
    private TextView recentTitle;
    private RecyclerView recentList;
    private RecentAdapter recentAdapter;
    // 뷰 필드보다 아래에 선언해야 한다 (위에 두면 컴파일 오류: illegal forward reference)
    private final Runnable showSlowNotice = () -> stateText.setText(R.string.home_server_waking);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        swipe = findViewById(R.id.home_swipe);
        list = findViewById(R.id.home_trend_list);
        skeleton = findViewById(R.id.home_skeleton);
        stateText = findViewById(R.id.home_state_text);
        retryButton = findViewById(R.id.home_retry);
        searchInput = findViewById(R.id.home_search_input);
        ImageButton searchButton = findViewById(R.id.home_search_button);

        // 검색: 키보드의 검색 키 또는 돋보기 버튼
        searchButton.setOnClickListener(v -> search());
        findViewById(R.id.home_scan_button).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class)));   // F-16 모델명 스캔
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search();
                return true;
            }
            return false;
        });

        setupShortcuts();
        setupCategories();
        setupRecent();

        adapter = new TrendAdapter(this::onTrendClick);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        swipe.setOnRefreshListener(this::loadTrend);   // 아래로 당겨서 새로고침
        retryButton.setOnClickListener(v -> loadTrend());

        loadTrend();
    }

    // ── 오른쪽 위 메뉴: 마이페이지 ───────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_mypage) {
            startActivity(new Intent(this, MyPageActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── 바로가기: AI 추천 · AI 상담 · 가격 알림 ───────────────

    private void setupShortcuts() {
        bindShortcut(R.id.home_shortcut_recommend, "🤖", R.string.recommend_title, RecommendActivity.class);
        bindShortcut(R.id.home_shortcut_chat, "💬", R.string.chat_title, ChatActivity.class);
        // 로그인 안 했으면 알림 화면이 로그인부터 띄운다
        bindShortcut(R.id.home_shortcut_alerts, "🔔", R.string.alert_title, AlertListActivity.class);
    }

    /** 바로가기 버튼 하나(item_home_shortcut.xml)에 그림 · 이름 · 이동할 화면을 넣는다 */
    private void bindShortcut(int viewId, String icon, int label, Class<?> target) {
        View shortcut = findViewById(viewId);
        ((TextView) shortcut.findViewById(R.id.shortcut_icon)).setText(icon);
        ((TextView) shortcut.findViewById(R.id.shortcut_label)).setText(label);
        shortcut.setOnClickListener(v -> startActivity(new Intent(this, target)));
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

    /**
     * 저장해 둔 트렌드가 있으면 먼저 보여 주고, 서버의 최신 목록으로 뒤에서 바꿔치기한다.
     * 서버가 잠들어 있어도 사용자는 바로 목록을 볼 수 있다.
     */
    private void loadTrend() {
        boolean showingList = list.getVisibility() == View.VISIBLE;
        if (!showingList) {
            TrendResponse cached = TrendCache.load(this);
            if (cached != null) {
                adapter.setItems(cached.items);
                showList();
                showingList = true;
            }
        }

        if (showingList) {
            swipe.setRefreshing(true);   // 목록은 그대로 두고 위쪽 동그라미로만 "갱신 중" 표시
        } else {
            showLoading();
            handler.postDelayed(showSlowNotice, SLOW_NOTICE_MS);
        }

        ApiClient.get().getTrend(null).enqueue(new Callback<TrendResponse>() {
            @Override
            public void onResponse(@NonNull Call<TrendResponse> call,
                                   @NonNull Response<TrendResponse> response) {
                TrendResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    onTrendFailed(getString(R.string.home_trend_failed));
                } else if (body.items == null || body.items.isEmpty()) {
                    onTrendFailed(getString(R.string.home_trend_empty));
                } else {
                    finishLoading();
                    TrendCache.save(MainActivity.this, body);
                    adapter.setItems(body.items);
                    showList();
                }
            }

            @Override
            public void onFailure(@NonNull Call<TrendResponse> call, @NonNull Throwable t) {
                onTrendFailed(getString(R.string.home_trend_failed));
            }
        });
    }

    /** 새 목록을 못 받았을 때: 지난 목록을 보고 있었다면 그대로 두고 짧게 알리기만 한다 */
    private void onTrendFailed(String text) {
        finishLoading();
        if (list.getVisibility() == View.VISIBLE) {
            Toast.makeText(this, R.string.home_trend_stale, Toast.LENGTH_SHORT).show();
        } else {
            showError(text);
        }
    }

    private void finishLoading() {
        handler.removeCallbacks(showSlowNotice);
        swipe.setRefreshing(false);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(showSlowNotice);
        Pulse.stop(skeleton);
        super.onDestroy();
    }

    /** 트렌드 제품을 누르면 바로 리포트 화면으로 (TrendItem도 Product라서 그대로 넘길 수 있다) */
    private void onTrendClick(TrendItem item) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, item);
        startActivity(intent);
    }

    private void showLoading() {
        setSkeleton(true);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(R.string.home_trend_loading);
        retryButton.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
    }

    private void showList() {
        setSkeleton(false);
        stateText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
    }

    private void showError(String text) {
        setSkeleton(false);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
        retryButton.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
    }

    private void setSkeleton(boolean on) {
        skeleton.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) Pulse.start(skeleton);
        else Pulse.stop(skeleton);
    }
}
