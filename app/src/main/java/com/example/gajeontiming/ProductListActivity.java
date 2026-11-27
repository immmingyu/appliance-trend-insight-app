package com.example.gajeontiming;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.os.Bundle;
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

import com.example.gajeontiming.ui.Pulse;
import com.example.gajeontiming.adapter.ProductAdapter;
import com.example.gajeontiming.model.Product;
import com.example.gajeontiming.model.ProductListResponse;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.example.gajeontiming.network.StaleNotice;
import com.example.gajeontiming.scan.AccessoryFilter;
import com.example.gajeontiming.scan.ModelNameExtractor;
import com.example.gajeontiming.scan.SearchSuggester;
import com.google.android.material.chip.Chip;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-04 제품 목록 화면.
 * 홈에서 검색어(필수)와 품목(선택)을 인텐트로 받아 검색하고, 정렬 · 더 보기를 지원한다.
 */
public class ProductListActivity extends AppCompatActivity {

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_CATEGORY = "category";
    /** true면 "고르기 모드": 제품을 누르면 리포트 대신 그 제품을 부른 화면에 돌려준다 (비교용) */
    public static final String EXTRA_PICK_MODE = "pick_mode";
    public static final String EXTRA_EXCLUDE = "exclude";   // 목록에서 뺄 제품 (비교의 첫 제품)
    public static final String EXTRA_NOTICE = "notice";     // 목록 위에 띄울 안내 (예: 브랜드로 검색했어요)
    public static final String RESULT_PRODUCT = "picked_product";

    private static final int PAGE_SIZE = 15;

    private String query;             // 사용자가 처음 검색한 말
    private String category;          // null이면 품목 필터 없이 검색

    /**
     * 결과가 0건일 때 차례로 넓혀 볼 검색 방법들.
     * [0] 원래 검색어 + 품목 → [1..] 모델명 뒷자리를 줄인 검색어 + 품목 → [마지막] 원래 검색어, 품목 없이
     * 품목을 빼면 서버의 소모품 차단도 꺼지므로 가장 마지막에 시도한다.
     */
    private static class Attempt {
        final String query;
        final String category;
        Attempt(String query, String category) { this.query = query; this.category = category; }
    }
    private final List<Attempt> attempts = new ArrayList<>();
    private int attemptIndex = 0;
    private TextView fallbackNotice;
    private TextView staleNotice;            // 다나와 점검 중 저장된 정보 안내
    private ChipGroup suggestGroup;          // 본품을 못 찾았을 때 다음 검색어 제안
    private Button danawaButton;             // 다나와에서 직접 보기
    private final List<String> removedTitles = new ArrayList<>();   // 소모품으로 걸러낸 제목들
    private boolean pickMode;
    private Product exclude;
    private String notice;

    // ── 품목 둘러보기 (품목으로 들어왔을 때만): 품목 안에서 검색 + 브랜드 고르기 ──
    private String browseCategory;    // null이면 일반 검색 화면 (둘러보기 아님)
    private String browseKeyword = "";
    private String browseBrand;       // null이면 전체
    private EditText browseInput;
    private static final int BRAND_SAMPLE = 40;   // 브랜드 목록을 만들 때 살펴볼 제품 수
    private static final int MAX_BRANDS = 8;
    private String sort = "sim";      // sim | asc | dsc
    private int page = 1;
    private boolean loading = false;
    private boolean hasMore = true;
    private Call<ProductListResponse> currentCall;

    private RecyclerView list;
    private View skeleton;        // 첫 페이지를 불러오는 동안 보여 주는 안내 + 빈 카드
    private View skeletonCards;   // 그중 깜빡이는 빈 카드 부분
    private TextView skeletonText;
    private TextView stateText;
    private Button retryButton;
    private ProductAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        query = getIntent().getStringExtra(EXTRA_QUERY);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        pickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);
        exclude = (Product) getIntent().getSerializableExtra(EXTRA_EXCLUDE);
        notice = getIntent().getStringExtra(EXTRA_NOTICE);
        buildAttempts();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pickMode ? getString(R.string.pick_title, query) : query);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);   // 왼쪽 위 뒤로 가기 화살표
        }

        list = findViewById(R.id.product_list);
        skeleton = findViewById(R.id.product_skeleton);
        skeletonCards = findViewById(R.id.product_skeleton_cards);
        skeletonText = findViewById(R.id.product_skeleton_text);
        stateText = findViewById(R.id.product_state_text);
        retryButton = findViewById(R.id.product_retry);
        fallbackNotice = findViewById(R.id.product_fallback_notice);
        staleNotice = findViewById(R.id.product_stale_notice);
        suggestGroup = findViewById(R.id.product_suggest_group);
        danawaButton = findViewById(R.id.product_danawa);
        danawaButton.setOnClickListener(v -> openDanawa());
        setupBrowse();

        adapter = new ProductAdapter(this::onProductClick);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        list.setLayoutManager(lm);
        list.setAdapter(adapter);

        // 끝에서 3개 전까지 내려오면 다음 페이지를 불러온다 (더 보기)
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                if (lm.findLastVisibleItemPosition() >= adapter.getProductCount() - 3) {
                    loadPage(page + 1);
                }
            }
        });

        ChipGroup sortGroup = findViewById(R.id.product_sort_group);
        sortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String newSort = id == R.id.sort_low ? "asc" : id == R.id.sort_high ? "dsc" : "sim";
            if (!newSort.equals(sort)) {
                sort = newSort;
                restart();
            }
        });

        retryButton.setOnClickListener(v -> loadPage(1));

        restart();
    }

    // ── 품목 둘러보기 ───────────────────────────────────────

    /** 품목 이름 그대로 들어왔으면(☰ 메뉴 · 홈 칩) 품목 안 검색창과 브랜드 칩을 켠다 */
    private void setupBrowse() {
        if (category == null || !category.equals(query)) return;
        browseCategory = category;
        findViewById(R.id.product_browse_box).setVisibility(View.VISIBLE);

        browseInput = findViewById(R.id.product_browse_input);
        browseInput.setHint(getString(R.string.browse_hint, browseCategory));
        ImageButton searchButton = findViewById(R.id.product_browse_search);
        searchButton.setOnClickListener(v -> onBrowseSearch());
        browseInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                onBrowseSearch();
                return true;
            }
            return false;
        });
        loadBrands();
    }

    private void onBrowseSearch() {
        browseKeyword = browseInput.getText().toString().trim();   // 비우고 검색하면 품목 전체로
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(browseInput.getWindowToken(), 0);
        browseInput.clearFocus();
        applyBrowse();
    }

    /**
     * 검색어를 "브랜드 + 검색어 + 품목"으로 만든다 (예: "삼성전자 무풍 에어컨").
     * 서버의 품목 필터가 느슨해서 "삼성"만 보내면 SSD · 김치냉장고까지 나오므로 품목 이름을 꼭 붙인다.
     */
    private void applyBrowse() {
        StringBuilder sb = new StringBuilder();
        if (browseBrand != null) sb.append(browseBrand).append(' ');
        if (!browseKeyword.isEmpty()) sb.append(browseKeyword).append(' ');
        sb.append(browseCategory);
        query = sb.toString();
        buildAttempts();
        restart();
    }

    /**
     * 이 품목을 한 번 넉넉히(40개) 검색해서 2개 이상 나온 브랜드로 칩을 만든다.
     * 품목마다 브랜드가 달라서 고정 목록 대신 결과에서 뽑는다 (판매처 이름 같은 1건짜리는 빠진다).
     * 실패하면 브랜드 줄만 안 보이고 나머지는 그대로 쓸 수 있다.
     */
    private void loadBrands() {
        ApiClient.get().searchProducts(browseCategory, 1, BRAND_SAMPLE, "sim", browseCategory)
                .enqueue(new Callback<ProductListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductListResponse> call,
                                           @NonNull Response<ProductListResponse> response) {
                        ProductListResponse body = response.body();
                        if (isFinishing() || body == null || body.items == null) return;
                        Map<String, Integer> counts = new LinkedHashMap<>();
                        for (Product p : body.items) {
                            if (p.brand == null || p.brand.trim().isEmpty()) continue;
                            counts.merge(p.brand.trim(), 1, Integer::sum);
                        }
                        List<String> brands = new ArrayList<>();
                        counts.entrySet().stream()
                                .filter(e -> e.getValue() >= 2)
                                .sorted((a, b) -> b.getValue() - a.getValue())
                                .limit(MAX_BRANDS)
                                .forEach(e -> brands.add(e.getKey()));
                        showBrands(brands);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                        // 브랜드 칩은 없어도 되는 기능이라 조용히 넘어간다
                    }
                });
    }

    /**
     * 브랜드를 골랐으면 그 브랜드 제품만 남긴다.
     * "캐리어 21평 에어컨"처럼 브랜드 이름이 다른 뜻(여행가방)도 있으면 서버가 엉뚱한 제품을 섞어 보내서다.
     */
    private List<Product> keepChosenBrand(List<Product> items) {
        if (items == null || browseBrand == null) return items;
        List<Product> kept = new ArrayList<>();
        for (Product p : items) {
            if (p.brand != null && browseBrand.equals(p.brand.trim())) kept.add(p);
        }
        return kept;
    }

    /** 다나와 점검으로 저장된 정보를 받았으면 목록 위에 언제 기준인지 알린다 */
    private void showStaleNotice(String text) {
        staleNotice.setText(text);
        staleNotice.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    /** 비교의 첫 제품은 B 후보에서 뺀다 */
    private List<Product> removeExcluded(List<Product> items) {
        if (items == null || exclude == null) return items;
        List<Product> kept = new ArrayList<>();
        for (Product p : items) {
            if (!Product.isSame(exclude, p)) kept.add(p);
        }
        return kept;
    }

    private void showBrands(List<String> brands) {
        if (brands.size() < 2) return;   // 브랜드가 하나뿐이면 고를 의미가 없다
        ChipGroup group = findViewById(R.id.product_brand_group);
        LayoutInflater inflater = LayoutInflater.from(this);
        Chip all = (Chip) inflater.inflate(R.layout.item_filter_chip, group, false);
        all.setText(R.string.brand_all);
        all.setId(View.generateViewId());
        group.addView(all);
        for (String brand : brands) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, group, false);
            chip.setText(brand);
            chip.setTag(brand);
            chip.setId(View.generateViewId());
            group.addView(chip);
        }
        group.check(all.getId());
        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Object tag = g.findViewById(checkedIds.get(0)).getTag();
            String brand = tag instanceof String ? (String) tag : null;
            if (brand == null ? browseBrand == null : brand.equals(browseBrand)) return;
            browseBrand = brand;
            applyBrowse();
        });
        findViewById(R.id.product_brand_scroll).setVisibility(View.VISIBLE);
    }

    private void buildAttempts() {
        attempts.clear();
        attempts.add(new Attempt(query, category));
        // 검색어가 모델명처럼 생겼으면 뒷자리를 줄인 검색어 (품목은 그대로 붙여 소모품을 거른다)
        if (ModelNameExtractor.looksLikeModel(query)) {
            for (String shorter : ModelNameExtractor.shorterQueries(query)) {
                attempts.add(new Attempt(shorter, category));
            }
        }
        // 마지막 수단: 품목 필터 없이 원래 검색어로
        if (category != null) attempts.add(new Attempt(query, null));
    }

    /**
     * 모델명으로 찾는데 품목 필터가 없으면 서버가 소모품을 거르지 않으므로 앱이 거른다.
     * (일반 검색어나 품목을 아는 검색은 서버 결과를 그대로 쓴다)
     */
    private List<Product> removeAccessoriesIfNeeded(List<Product> items) {
        Attempt a = current();
        if (items == null || a.category != null || !ModelNameExtractor.looksLikeModel(query)) return items;
        List<Product> kept = new ArrayList<>();
        for (Product p : items) {
            if (AccessoryFilter.isAccessory(p.title, p.price)) {
                removedTitles.add(p.title);    // 버린 제목은 "다음 검색어 제안"의 힌트로 쓴다
            } else {
                kept.add(p);
            }
        }
        return kept;
    }

    private Attempt current() {
        return attempts.get(attemptIndex);
    }

    /** 정렬이 바뀌면 처음부터 다시 검색 (넓혀 보던 단계도 처음으로) */
    private void restart() {
        if (currentCall != null) currentCall.cancel();
        adapter.clear();
        hasMore = true;
        attemptIndex = 0;
        removedTitles.clear();
        fallbackNotice.setVisibility(View.GONE);
        staleNotice.setVisibility(View.GONE);
        suggestGroup.setVisibility(View.GONE);
        danawaButton.setVisibility(View.GONE);
        loadPage(1);
    }

    private void loadPage(int target) {
        loading = true;
        showLoading(target == 1);

        Attempt a = current();
        currentCall = ApiClient.get().searchProducts(a.query, target, PAGE_SIZE, sort, a.category);
        currentCall.enqueue(new Callback<ProductListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> call,
                                   @NonNull Response<ProductListResponse> response) {
                loading = false;
                ProductListResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    // 서버가 보낸 안내(예: 503 "상품 검색이 잠시 느려요…")가 있으면 그대로 보여 준다
                    showError(response.code() == 429
                            ? "요청이 많아요. 잠시 후 다시 시도해 주세요."
                            : ApiErrors.message(response, "검색 결과를 불러오지 못했어요."));
                    return;
                }
                List<Product> items = body.items;
                page = target;
                if (target == 1) showStaleNotice(StaleNotice.message(body.stale, body.staleAt));
                // 한 페이지를 꽉 채워 오지 않았으면 마지막 페이지로 본다
                hasMore = items != null && items.size() >= PAGE_SIZE;

                List<Product> shown = removeExcluded(keepChosenBrand(removeAccessoriesIfNeeded(items)));
                if (shown != null) adapter.addItems(shown);

                if (adapter.getProductCount() == 0) {
                    // 첫 페이지가 0건이면 다음 방법으로 넓혀서 다시 찾는다
                    if (target == 1 && attemptIndex < attempts.size() - 1) {
                        attemptIndex++;
                        loadPage(1);
                        return;
                    }
                    showEmpty();
                } else {
                    showFallbackNoticeIfNeeded();
                    showList();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;   // 정렬을 바꿔서 취소한 요청은 무시
                loading = false;
                showError("인터넷 연결을 확인해 주세요.");
            }
        });
    }

    /** 제품을 누르면 그 제품 정보를 통째로 넘기며 리포트 화면을 연다 (고르기 모드면 돌려준다) */
    /** 원래 검색어로 못 찾고 넓혀서 찾았으면, 무엇으로 찾은 결과인지 알려준다 */
    private void showFallbackNoticeIfNeeded() {
        if (attemptIndex == 0) {
            if (notice != null) fallbackNotice.setText(notice);
            fallbackNotice.setVisibility(notice != null ? View.VISIBLE : View.GONE);
            return;
        }
        Attempt a = current();
        fallbackNotice.setText(a.query.equals(query)
                ? getString(R.string.fallback_no_category, query)
                : getString(R.string.fallback_shorter, query, a.query));
        fallbackNotice.setVisibility(View.VISIBLE);
    }

    private void onProductClick(Product product) {
        if (pickMode) {
            Intent result = new Intent();
            result.putExtra(RESULT_PRODUCT, product);
            setResult(RESULT_OK, result);
            finish();
            return;
        }
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, product);
        // 칩이나 검색어로 품목을 알고 있으면 같이 넘긴다 (제품명에 품목 이름이 없는 경우 대비)
        intent.putExtra(ProductDetailActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
    }

    // ── 화면 상태 ─────────────────────────────────────────────

    /** 첫 페이지면 가운데에 크게, 더 보기면 목록 아래 작은 표시 */
    private void showLoading(boolean firstPage) {
        retryButton.setVisibility(View.GONE);
        if (firstPage) {
            list.setVisibility(View.GONE);
            stateText.setVisibility(View.GONE);
            setSkeleton(true);
        } else {
            // 스크롤 도중에는 목록을 바로 바꿀 수 없어서 다음 순서로 미룬다
            list.post(() -> { if (loading) adapter.setLoadingFooter(true); });
        }
    }

    private void showList() {
        adapter.setLoadingFooter(false);
        setSkeleton(false);
        stateText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        setSkeleton(false);
        list.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);

        boolean isModel = ModelNameExtractor.looksLikeModel(query);
        if (isModel && !removedTitles.isEmpty()) {
            stateText.setText(getString(R.string.empty_only_accessories, query));
        } else {
            stateText.setText(getString(R.string.empty_default, query));
        }

        // 다음 검색어 제안: 걸러낸 소모품 제목에서 브랜드 · 품목을 뽑아 만든다
        suggestGroup.removeAllViews();
        List<String> suggestions = isModel
                ? SearchSuggester.suggest(query, category, removedTitles)
                : new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String sgt : suggestions) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, suggestGroup, false);
            chip.setText(sgt);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> searchAgain(sgt));
            suggestGroup.addView(chip);
        }
        suggestGroup.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
        danawaButton.setVisibility(isModel ? View.VISIBLE : View.GONE);
    }

    /** 제안 칩을 누르면 그 검색어로 이 화면에서 다시 검색한다 */
    private void searchAgain(String newQuery) {
        query = newQuery;
        String detected = com.example.gajeontiming.model.Categories.detect(newQuery);
        if (detected != null) category = detected;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pickMode ? getString(R.string.pick_title, query) : query);
        }
        buildAttempts();
        restart();
    }

    /** 앱이 못 찾는 제품을 사용자가 직접 확인할 수 있게 다나와 검색 결과를 브라우저로 연다 */
    private void openDanawa() {
        try {
            Uri uri = Uri.parse("https://search.danawa.com/dsearch.php?query=" + Uri.encode(query));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String text) {
        adapter.setLoadingFooter(false);
        setSkeleton(false);
        if (adapter.getProductCount() > 0) {
            // 더 보기 중 실패: 이미 본 목록은 그대로 두고 안내만. 다시 스크롤하면 재시도된다
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            return;
        }
        list.setVisibility(View.GONE);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(text);
        retryButton.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {   // 왼쪽 위 화살표 → 홈으로
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (currentCall != null) currentCall.cancel();
        Pulse.stop(skeletonCards);
        super.onDestroy();
    }

    /** 빈 카드만 있으면 "결과 없음"처럼 보여서, 위에 검색 중 안내를 같이 띄운다 */
    private void setSkeleton(boolean on) {
        skeleton.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            skeletonText.setText(getString(R.string.product_searching, query));
            Pulse.start(skeletonCards);
        } else {
            Pulse.stop(skeletonCards);
        }
    }
}
