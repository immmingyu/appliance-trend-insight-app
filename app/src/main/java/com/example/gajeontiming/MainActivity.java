package com.example.gajeontiming;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.gajeontiming.adapter.RecentAdapter;
import com.example.gajeontiming.adapter.TrendAdapter;
import com.example.gajeontiming.data.RecentProductDb;
import com.example.gajeontiming.data.TrendCache;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.TrendItem;
import com.example.gajeontiming.model.TrendResponse;
import com.example.gajeontiming.alert.ApprovalCheckWorker;
import com.example.gajeontiming.alert.PushTokens;
import com.example.gajeontiming.auth.AccountAware;
import com.example.gajeontiming.auth.AccountSync;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.ui.Pulse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 홈 화면.
 * F-02 트렌드 TOP10 · F-03 카테고리 선택 · 검색창 → 제품 목록 화면으로 이동
 */
public class MainActivity extends AppCompatActivity implements AccountAware {

    private static final long SLOW_NOTICE_MS = 5000;   // 5초가 넘으면 "서버 깨우는 중" 안내로 바꿈

    private final Handler handler = new Handler(Looper.getMainLooper());

    private DrawerLayout drawer;
    private boolean autoRetried;   // 트렌드를 못 받아 자동으로 한 번 다시 요청했는지
    private TextView drawerUser;   // ☰ 메뉴 맨 위 로그인 상태
    private MenuItem productsItem;   // ☰ 메뉴의 "제품 둘러보기" (누르면 카테고리가 펼쳐짐)
    private final List<MenuItem> categoryItems = new ArrayList<>();
    private boolean categoriesOpen = false;
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
        // 앱 이름은 툴바 안의 굵은 글씨(activity_main.xml)로 보여 주므로 기본 제목은 끈다
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        setupDrawer(toolbar);

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

        setupCategories();
        setupRecent();

        adapter = new TrendAdapter(this::onTrendClick);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        swipe.setOnRefreshListener(this::loadTrend);   // 아래로 당겨서 새로고침
        retryButton.setOnClickListener(v -> loadTrend());

        loadTrend();
        // 로그인 상태를 서버와 맞추는 건 onResume에서 한다 (앱에 돌아올 때마다 · 1분 간격)
    }

    // ── ☰ 메뉴: AI 추천 · AI 상담 · 가격 알림 · 마이페이지 ───────

    private void setupDrawer(MaterialToolbar toolbar) {
        drawer = findViewById(R.id.home_drawer);
        NavigationView nav = findViewById(R.id.home_nav);
        toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        nav.setNavigationItemSelectedListener(this::onDrawerItem);
        setupDrawerCategories(nav.getMenu());

        // 머리 부분(앱 이름 · 로그인 상태)을 누르면 마이페이지 (로그인은 마이페이지 안에서)
        View header = nav.getHeaderView(0);
        drawerUser = header.findViewById(R.id.drawer_user);
        header.setOnClickListener(v -> openFromDrawer(MyPageActivity.class));

        // 메뉴가 열려 있을 때 뒤로 가기 → 앱을 닫지 말고 메뉴만 닫는다
        OnBackPressedCallback closeDrawer = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawer.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeDrawer);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View v) {
                closeDrawer.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View v) {
                closeDrawer.setEnabled(false);
            }
        });
    }

    /** "제품 둘러보기" 바로 아래에 카테고리를 숨긴 채로 넣어 둔다 (누르면 펼쳐짐) */
    private void setupDrawerCategories(Menu menu) {
        productsItem = menu.findItem(R.id.drawer_products);
        int order = productsItem.getOrder() + 1;
        for (String name : Categories.ALL) {
            MenuItem item = menu.add(R.id.drawer_group_products, View.generateViewId(), order++, name);
            item.setVisible(false);
            categoryItems.add(item);
        }
        setCategoriesOpen(false);
    }

    private void setCategoriesOpen(boolean open) {
        categoriesOpen = open;
        for (MenuItem item : categoryItems) item.setVisible(open);
        productsItem.setTitle(open ? R.string.drawer_products_close : R.string.drawer_products_open);
    }

    private boolean onDrawerItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.drawer_products) {
            // 메뉴는 닫지 않고 목록만 펼치거나 접는다.
            // 클릭을 처리하는 동안 NavigationView가 목록 갱신을 멈춰 두므로, 처리가 끝난 뒤에 바꾼다
            drawer.post(() -> setCategoriesOpen(!categoriesOpen));
            return false;   // 선택 표시(하이라이트)는 남기지 않는다
        }
        if (categoryItems.contains(item)) {
            String name = item.getTitle().toString();
            drawer.closeDrawer(GravityCompat.START);
            openProductList(name, name);   // 카테고리 제품 목록 → 제품을 누르면 상세(리포트)
            return true;
        }
        if (id == R.id.drawer_recommend) openFromDrawer(RecommendActivity.class);
        else if (id == R.id.drawer_chat) openFromDrawer(ChatActivity.class);
        // 로그인 안 했으면 알림 화면이 로그인부터 띄운다
        else if (id == R.id.drawer_alerts) openFromDrawer(AlertListActivity.class);
        else if (id == R.id.drawer_mypage) openFromDrawer(MyPageActivity.class);
        else return false;
        return true;
    }

    private void openFromDrawer(Class<?> target) {
        drawer.closeDrawer(GravityCompat.START);
        startActivity(new Intent(this, target));
    }

    /** 로그인 · 로그아웃하고 돌아올 수 있으니 화면에 돌아올 때마다 새로 쓴다 */
    /** 안드로이드 13+: 승인 대기 사업자가 승인 결과 푸시를 받으려면 알림 권한이 필요하다 */
    private final ActivityResultLauncher<String> pushPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });
    private boolean pushPermissionAsked;   // 이 화면에서 한 번만 묻는다 (거절하면 다시 귀찮게 하지 않음)

    /**
     * 가입 신청 화면을 거치지 않고 로그인한 승인 대기 사업자도 푸시를 받을 수 있게 권한을 묻는다.
     * (가입 신청 직후에는 PendingApprovalActivity가 묻는다)
     */
    private void askPushPermissionIfPending() {
        if (pushPermissionAsked || !SessionManager.isB2bPending()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !isFinishing()) {
            pushPermissionAsked = true;
            pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** 서버와 회원 정보를 맞춘 뒤: 드로어 이름 갱신 · 푸시 권한 · 토큰 등록 · 승인 확인 예약 */
    private void onAccountSynced() {
        bindDrawerUser();
        askPushPermissionIfPending();
        PushTokens.register();            // 로그인 상태면 이 휴대폰 푸시 토큰 등록 (같은 토큰은 덮어씀)
        ApprovalCheckWorker.sync(this);   // 승인 대기 중이면 백그라운드 확인 예약, 아니면 멈춤
        AccountSync.showStatusNotice();   // 상태가 바뀐 걸 방금 알았으면 안내 창
    }

    /** 승인 · 거절 푸시로 회원 정보가 바뀌었을 때 (AccountSync.notifyChanged) */
    @Override
    public void onAccountChanged() {
        onAccountSynced();
    }

    private void bindDrawerUser() {
        String name = SessionManager.getName();
        if (SessionManager.isLoggedIn()) {
            drawerUser.setText(getString(R.string.drawer_user,
                    name != null && !name.isEmpty() ? name : SessionManager.getEmail()));
        } else {
            drawerUser.setText(R.string.drawer_guest);
        }
    }

    /** 리포트를 보고 돌아오면 최근 본 제품을 새로 그린다 */
    @Override
    protected void onResume() {
        super.onResume();
        bindRecent();
        // 저장된 로그인 상태를 서버와 맞춘다 (회원 유형 · 등급 · 사업자 승인). 화면은 기다리지 않는다
        // (승인 대기 중 결과 확인 · 안내 창은 어느 화면이든 AccountSync.onScreenResumed가 한다)
        AccountSync.refreshIfStale(this::onAccountSynced);
        bindDrawerUser();
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
        autoRetried = false;
        // 목록 칸이 보이는지가 아니라 실제 항목이 있는지로 판단한다
        // (빈 목록을 "보여 주는 중"으로 착각해서 저장된 목록 · 로딩 표시 · 오류 안내가 모두 안 나오던 문제)
        boolean showingList = hasTrendItems();
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
        requestTrend();
    }

    private void requestTrend() {
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
        if (isFinishing() || isDestroyed()) return;
        // 보여 줄 목록이 하나도 없으면 한 번은 자동으로 다시 요청한다.
        // 서버는 재시작 직후 첫 트렌드 계산에 90초 넘게 걸려 시간 초과가 나지만, 두 번째 요청은 금방 온다
        if (!hasTrendItems() && !autoRetried) {
            autoRetried = true;
            stateText.setText(R.string.home_server_waking);
            requestTrend();
            return;
        }
        finishLoading();
        if (hasTrendItems()) {
            Toast.makeText(this, R.string.home_trend_stale, Toast.LENGTH_SHORT).show();
        } else {
            showError(text);
        }
    }

    private boolean hasTrendItems() {
        return adapter.getItemCount() > 0;
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
