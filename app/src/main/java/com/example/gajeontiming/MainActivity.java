package com.example.gajeontiming;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.gajeontiming.adapter.RecentAdapter;
import com.example.gajeontiming.adapter.TrendAdapter;
import com.example.gajeontiming.alert.ApprovalCheckWorker;
import com.example.gajeontiming.alert.PushTokens;
import com.example.gajeontiming.auth.AccountAware;
import com.example.gajeontiming.auth.AccountSync;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.b2b.MarketSearchActivity;
import com.example.gajeontiming.b2b.PriceMonitorActivity;
import com.example.gajeontiming.data.DrawerPrefs;
import com.example.gajeontiming.data.RecentProductDb;
import com.example.gajeontiming.data.TrendCache;
import com.example.gajeontiming.model.Categories;
import com.example.gajeontiming.model.TrendItem;
import com.example.gajeontiming.model.Tiers;
import com.example.gajeontiming.model.TrendResponse;
import com.example.gajeontiming.model.UserSummary;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.AuthGuard;
import com.example.gajeontiming.ui.Pulse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

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
    // ☰ 메뉴
    private final List<Chip> drawerChips = new ArrayList<>();   // "제품 둘러보기" 아래 품목 칩
    private boolean categoriesOpen = true;
    private boolean categoriesTouched;      // 사용자가 직접 펼치거나 접었는지
    private UserSummary summary;            // 서버 요약 (없거나 실패하면 null → 숫자 "–")
    private Call<UserSummary> summaryCall;  // 받는 중인 요청 (겹쳐 부르지 않는다)
    private long summaryLoadedAt;
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
        // 확장: 홈의 매장 모드 카드 → 매장 모드 (카드 전체와 버튼 모두)
        findViewById(R.id.home_store_card).setOnClickListener(v ->
                startActivity(new Intent(this, StoreModeActivity.class)));
        findViewById(R.id.home_store_button).setOnClickListener(v ->
                startActivity(new Intent(this, StoreModeActivity.class)));
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

    // ── ☰ 메뉴 ────────────────────────────────────────────────
    // 머리 영역 → 요약 3칸(로그인 전에는 로그인 카드) → 매장 모드 → 둘러보기 · AI 도우미 · 비즈니스 → 맨 아래
    // 화면은 drawer_content.xml. 요약 숫자는 서버(/api/user/summary)를 기다리지 않고 "–"로 먼저 그린다

    private static final long SUMMARY_FRESH_MS = 10_000;   // 메뉴를 연달아 열 때 요약을 다시 부르지 않는 간격

    private void setupDrawer(MaterialToolbar toolbar) {
        drawer = findViewById(R.id.home_drawer);
        drawer.setDrawerElevation(0f);   // 그림자 없음
        applyDrawerWidth();
        applyDrawerInsets();
        toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));

        // ① 머리 영역 (로그인했을 때만 마이페이지로)
        findViewById(R.id.drawer_close).setOnClickListener(v -> drawer.closeDrawer(GravityCompat.START));
        findViewById(R.id.drawer_header).setOnClickListener(v -> {
            if (SessionManager.isLoggedIn()) openFromDrawer(MyPageActivity.class);
        });
        findViewById(R.id.drawer_upgrade).setOnClickListener(v -> openFromDrawer(MyPageActivity.class));

        // ② 요약 칸 · 로그인 카드
        findViewById(R.id.drawer_sum_alerts).setOnClickListener(v -> openFromDrawer(AlertListActivity.class));
        findViewById(R.id.drawer_sum_middle).setOnClickListener(v -> openFromDrawer(
                summary != null && summary.marketAlertsToday != null
                        ? PriceMonitorActivity.class : AlertListActivity.class));
        findViewById(R.id.drawer_sum_recent).setOnClickListener(v -> openFromDrawer(MyPageActivity.class));
        findViewById(R.id.drawer_login).setOnClickListener(v -> openFromDrawer(LoginActivity.class));

        // ③ 매장 모드
        findViewById(R.id.drawer_store).setOnClickListener(v -> openFromDrawer(StoreModeActivity.class));

        // ④ 메뉴 줄
        View products = findViewById(R.id.drawer_row_products);
        bindRow(products, R.drawable.ic_apps, R.string.drawer_products, null);
        products.setOnClickListener(v -> {
            categoriesTouched = true;   // 직접 펼치고 접은 뒤에는 회원 유형에 따라 다시 바꾸지 않는다
            setCategoriesOpen(!categoriesOpen);
        });
        setupDrawerCategories();
        bindRow(findViewById(R.id.drawer_row_recommend), R.drawable.ic_tune, R.string.recommend_title,
                RecommendActivity.class);
        bindRow(findViewById(R.id.drawer_row_chat), R.drawable.ic_chat, R.string.chat_title, ChatActivity.class);
        // 로그인 안 했으면 알림 화면이 로그인부터 띄운다
        bindRow(findViewById(R.id.drawer_row_alerts), R.drawable.ic_bell, R.string.alert_title,
                AlertListActivity.class);
        bindRow(findViewById(R.id.drawer_row_monitor), R.drawable.ic_trending, R.string.monitor_title,
                PriceMonitorActivity.class);
        bindRow(findViewById(R.id.drawer_row_market), R.drawable.ic_analytics, R.string.market_title,
                MarketSearchActivity.class);
        setRowDesc(findViewById(R.id.drawer_row_recommend), getString(R.string.drawer_recommend_desc));
        setRowDesc(findViewById(R.id.drawer_row_chat), getString(R.string.drawer_chat_desc));
        setRowDesc(findViewById(R.id.drawer_row_monitor), getString(R.string.drawer_monitor_desc));
        setRowDesc(findViewById(R.id.drawer_row_market), getString(R.string.drawer_market_desc));

        // ⑥ 맨 아래
        findViewById(R.id.drawer_mypage).setOnClickListener(v -> openFromDrawer(MyPageActivity.class));
        findViewById(R.id.drawer_support).setOnClickListener(v -> mailSupport());
        findViewById(R.id.drawer_terms).setOnClickListener(v -> openUrl(AppLinks.TERMS_URL));

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
            public void onDrawerStateChanged(int newState) {
                // 열기 시작할 때 요약을 새로 부른다 (메뉴는 기다리지 않고 바로 열린다)
                if (newState != DrawerLayout.STATE_IDLE && !drawer.isDrawerOpen(GravityCompat.START)) {
                    bindDrawerLocal();
                    loadSummary(false);
                }
            }

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

    /**
     * 머리 영역이 상태 표시줄 · 카메라 구멍 밑으로 들어가지 않게, 그 높이만큼 위쪽 패딩을 더한다.
     * NavigationView가 시스템 여백을 받아 자식에게 넘기지 않으므로 NavigationView에서 받는다.
     * (기기 · 회전마다 값이 달라서 숫자로 고정하지 않는다)
     */
    private void applyDrawerInsets() {
        View header = findViewById(R.id.drawer_header);
        View footer = findViewById(R.id.drawer_footer);
        int baseTop = header.getPaddingTop();
        int baseBottom = footer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_nav), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;
            header.setPadding(header.getPaddingLeft(), baseTop + top,
                    header.getPaddingRight(), header.getPaddingBottom());
            // 맨 아래 줄이 제스처 바 · 3버튼 내비게이션 바에 가리지 않게
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            footer.setPadding(footer.getPaddingLeft(), footer.getPaddingTop(),
                    footer.getPaddingRight(), baseBottom + bottom);
            return insets;
        });
    }

    /**
     * 메뉴 너비 = 화면 너비 - 56dp, 최대 360dp (태블릿 · 가로 모드에서도 넘지 않게).
     * 화면이 돌거나 창 크기가 바뀌면 화면을 새로 만들면서 다시 계산된다.
     */
    private void applyDrawerWidth() {
        View nav = findViewById(R.id.home_nav);
        float density = getResources().getDisplayMetrics().density;
        int screen = Math.round(getResources().getConfiguration().screenWidthDp * density);
        int width = Math.min(screen - getResources().getDimensionPixelSize(R.dimen.drawer_side_gap),
                getResources().getDimensionPixelSize(R.dimen.drawer_max_width));
        ViewGroup.LayoutParams lp = nav.getLayoutParams();
        lp.width = width;
        nav.setLayoutParams(lp);
    }

    /** 메뉴 한 줄: 아이콘 · 제목 · 누르면 갈 화면. 지금 보고 있는 화면이면 옅은 주황으로 표시 */
    private void bindRow(View row, int icon, int title, Class<?> target) {
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        if (target == null) return;
        row.setOnClickListener(v -> openFromDrawer(target));
        if (target == getClass()) {   // 메뉴가 홈에만 있어서 지금은 해당 없음 · 다른 화면에 메뉴가 생기면 쓰인다
            int on = ContextCompat.getColor(this, R.color.on_brand_container);
            row.setBackgroundResource(R.drawable.bg_drawer_row_active);
            ((TextView) row.findViewById(R.id.row_title)).setTextColor(on);
            ((TextView) row.findViewById(R.id.row_desc)).setTextColor(on);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageTintList(ColorStateList.valueOf(on));
        }
    }

    /** 설명 줄: 있으면 보이고 줄 높이 56dp, 없으면 숨기고 48dp */
    private void setRowDesc(View row, CharSequence desc) {
        TextView d = row.findViewById(R.id.row_desc);
        boolean has = desc != null && desc.length() > 0;
        d.setText(desc);
        d.setVisibility(has ? View.VISIBLE : View.GONE);
        row.setMinimumHeight(dp(has ? 56 : 48));
    }

    /** "제품 둘러보기" 아래 품목 칩 (윤곽). 마지막에 본 품목만 선택 상태로 칠한다 (bindCategoryChips) */
    private void setupDrawerCategories() {
        ChipGroup group = findViewById(R.id.drawer_category_group);
        for (String name : Categories.ALL) {
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setOnClickListener(v -> {
                drawer.closeDrawer(GravityCompat.START);
                openProductList(name, name);   // 품목 제품 목록 → 제품을 누르면 상세(리포트)
            });
            group.addView(chip);
            drawerChips.add(chip);
        }
        setCategoriesOpen(true);
    }

    private void setCategoriesOpen(boolean open) {
        categoriesOpen = open;
        findViewById(R.id.drawer_category_group).setVisibility(open ? View.VISIBLE : View.GONE);
        ImageView end = findViewById(R.id.drawer_row_products).findViewById(R.id.row_end);
        end.setVisibility(View.VISIBLE);
        end.setImageResource(open ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    private void bindCategoryChips() {
        String last = DrawerPrefs.lastCategory(this);
        ColorStateList selectedBg = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_container));
        int selectedText = ContextCompat.getColor(this, R.color.on_brand_container);
        for (Chip chip : drawerChips) {
            boolean selected = chip.getText().toString().equals(last);
            if (chip.getTag() == null) {   // 처음 한 번: 테마 기본 모양을 기억해 둔다
                chip.setTag(new ColorStateList[]{chip.getChipBackgroundColor(), chip.getChipStrokeColor(),
                        chip.getTextColors()});
            }
            ColorStateList[] base = (ColorStateList[]) chip.getTag();
            chip.setChipBackgroundColor(selected ? selectedBg : base[0]);
            chip.setChipStrokeColor(selected ? selectedBg : base[1]);
            if (selected) chip.setTextColor(selectedText);
            else chip.setTextColor(base[2]);
        }
    }

    private void openFromDrawer(Class<?> target) {
        drawer.closeDrawer(GravityCompat.START);
        startActivity(new Intent(this, target));
    }

    /** 고객센터: 메일 앱으로 문의 (메일 앱이 없으면 주소만 안내) */
    private void mailSupport() {
        Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + AppLinks.SUPPORT_EMAIL));
        mail.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.drawer_support_subject));
        try {
            startActivity(mail);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.drawer_no_mail_app, AppLinks.SUPPORT_EMAIL),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

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

    /** 로그인 · 로그아웃 · 승인 상태가 바뀌었을 수 있으니 화면에 돌아올 때마다 다시 그리고 요약을 새로 부른다 */
    private void bindDrawerUser() {
        bindDrawerLocal();
        loadSummary(true);
    }

    /** 서버를 기다리지 않고 기기에 있는 값으로 메뉴를 그린다 (요약 숫자는 받은 게 없으면 "–") */
    private void bindDrawerLocal() {
        boolean loggedIn = SessionManager.isLoggedIn();
        if (!loggedIn) summary = null;
        boolean b2bType = summary != null ? "b2b".equals(summary.userType)
                : "b2b".equals(SessionManager.getUserType());

        // ① 머리 영역: 로그인 전에는 머리글자 · 이름 대신 "가전무쌍"
        findViewById(R.id.drawer_avatar).setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        findViewById(R.id.drawer_user_box).setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        findViewById(R.id.drawer_brand).setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        findViewById(R.id.drawer_header).setClickable(loggedIn);
        if (loggedIn) {
            String name = displayName();
            ((TextView) findViewById(R.id.drawer_name)).setText(name);
            ((TextView) findViewById(R.id.drawer_avatar)).setText(
                    name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1)));
            String tier = summary != null ? summary.tier : SessionManager.getTier();
            String tierLabel = summary != null && summary.tierLabel != null ? summary.tierLabel : Tiers.label(tier);
            ((TextView) findViewById(R.id.drawer_tier)).setText(getString(R.string.drawer_tier_line,
                    getString(b2bType ? R.string.drawer_type_b2b : R.string.drawer_type_b2c), tierLabel));
            boolean free = tier == null || tier.isEmpty() || "free".equals(tier);
            findViewById(R.id.drawer_upgrade).setVisibility(!b2bType && free ? View.VISIBLE : View.GONE);
        }

        // ② 요약 칸 / 로그인 카드
        findViewById(R.id.drawer_summary).setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        findViewById(R.id.drawer_guest).setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        if (loggedIn) bindSummaryNumbers();

        // ③ 매장 모드 마지막 결과
        bindStoreLast();

        // ④ 품목: 사업자는 메뉴가 길어서 접힌 채로 시작 (직접 펼치고 접었으면 그대로)
        boolean b2b = SessionManager.isB2b();
        if (!categoriesTouched) setCategoriesOpen(!b2b);
        bindCategoryChips();

        // ④ 가격 알림 설명 · 비즈니스 구역 (승인된 사업자만, 승인 · 거절 푸시나 앱 복귀로 바뀌면 여기서 다시 그린다)
        String alertDesc = null;
        if (!loggedIn) alertDesc = getString(R.string.drawer_alerts_guest);
        else if (summary != null && summary.alertsReached > 0)
            alertDesc = getString(R.string.drawer_alerts_reached, summary.alertsReached);
        setRowDesc(findViewById(R.id.drawer_row_alerts), alertDesc);
        findViewById(R.id.drawer_business).setVisibility(b2b ? View.VISIBLE : View.GONE);

        // ⑥ 마이페이지는 로그인했을 때만
        findViewById(R.id.drawer_mypage).setVisibility(loggedIn ? View.VISIBLE : View.INVISIBLE);
    }

    /** 닉네임 · 회사 이름 (서버 요약 → 기기에 저장된 이름 → 이메일 앞부분) */
    private String displayName() {
        if (summary != null && summary.displayName != null && !summary.displayName.isEmpty())
            return summary.displayName;
        String name = SessionManager.getName();
        if (name != null && !name.isEmpty()) return name;
        String email = SessionManager.getEmail();
        return email == null ? "" : email.split("@")[0];
    }

    /** 가격 알림 "2 / 3" · 목표가 도달 "1"(0보다 크면 주황) 또는 오늘 시장 알림 · 최근 본 제품 "4" */
    private void bindSummaryNumbers() {
        TextView alerts = findViewById(R.id.drawer_sum_alerts_value);
        TextView middleLabel = findViewById(R.id.drawer_sum_middle_label);
        TextView middle = findViewById(R.id.drawer_sum_middle_value);
        int ink = ContextCompat.getColor(this, R.color.ink);

        boolean market = summary != null && summary.marketAlertsToday != null;
        middleLabel.setText(market ? R.string.drawer_sum_market : R.string.drawer_sum_reached);
        if (summary == null) {
            alerts.setText(R.string.drawer_sum_empty);
            middle.setText(R.string.drawer_sum_empty);
            middle.setTextColor(ink);
        } else {
            SpannableStringBuilder a = new SpannableStringBuilder(String.valueOf(
                    summary.alerts != null ? summary.alerts.count : 0));
            if (summary.alerts != null && summary.alerts.limit > 0) {
                int start = a.length();
                a.append(" / ").append(String.valueOf(summary.alerts.limit));
                a.setSpan(new RelativeSizeSpan(0.75f), start, a.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                a.setSpan(new StyleSpan(Typeface.NORMAL), start, a.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                a.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.ink_2)),
                        start, a.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            alerts.setText(a);
            int n = market ? summary.marketAlertsToday : summary.alertsReached;
            middle.setText(String.valueOf(n));
            middle.setTextColor(n > 0 ? ContextCompat.getColor(this, R.color.brand_text) : ink);
        }
        ((TextView) findViewById(R.id.drawer_sum_recent_value))
                .setText(String.valueOf(RecentProductDb.get(this).count()));
    }

    /** 매장 모드 버튼 아래: 최근 찍은 제품 · 모델명 · 판단 배지 (저장된 결과가 없으면 숨김) */
    private void bindStoreLast() {
        String model = DrawerPrefs.storeModel(this);
        View strip = findViewById(R.id.drawer_store_last);
        if (model == null) {
            strip.setVisibility(View.GONE);
            return;
        }
        strip.setVisibility(View.VISIBLE);
        int score = DrawerPrefs.storeScore(this);
        ((TextView) findViewById(R.id.drawer_store_model)).setText(model);
        TextView badge = findViewById(R.id.drawer_store_verdict);
        badge.setText(DrawerPrefs.verdictLabel(score));
        badge.getBackground().mutate().setTint(ContextCompat.getColor(this, DrawerPrefs.verdictColor(score)));
    }

    /** 요약을 서버에서 받는다. 느리거나 실패하면 숫자만 "–"로 두고 메뉴는 그대로 쓴다 */
    private void loadSummary(boolean force) {
        if (!SessionManager.isLoggedIn() || summaryCall != null) return;
        if (!force && System.currentTimeMillis() - summaryLoadedAt < SUMMARY_FRESH_MS) return;
        summaryCall = ApiClient.get().getSummary();
        summaryCall.enqueue(new Callback<UserSummary>() {
            @Override
            public void onResponse(@NonNull Call<UserSummary> call, @NonNull Response<UserSummary> response) {
                summaryCall = null;
                if (AuthGuard.isLoggedOut(response)) {   // 로그인 만료 → 로그인 전 메뉴로
                    summary = null;
                } else if (response.isSuccessful() && response.body() != null) {
                    summary = response.body();
                    summaryLoadedAt = System.currentTimeMillis();
                } else {
                    summary = null;
                }
                if (!isFinishing()) bindDrawerLocal();
            }

            @Override
            public void onFailure(@NonNull Call<UserSummary> call, @NonNull Throwable t) {
                summaryCall = null;
                summary = null;
                if (!isFinishing()) bindDrawerLocal();
            }
        });
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
        DrawerPrefs.saveLastCategory(this, category);   // ☰ 메뉴 품목 칩 중 이것만 선택 상태
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
