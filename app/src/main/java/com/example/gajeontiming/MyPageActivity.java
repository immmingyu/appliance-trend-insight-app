package com.example.gajeontiming;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.adapter.RecentAdapter;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.data.RecentProductDb;
import com.example.gajeontiming.model.UserProfile;
import com.example.gajeontiming.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-15 마이페이지.
 * 위: 내 정보(로그인했을 때) 또는 로그인 안내 / 아래: F-08 최근 본 제품 전체 목록.
 * 로그인 · 회원가입 화면은 이 화면 아래 단계에 있다 (홈 → 마이페이지 → 로그인 → 회원가입).
 */
public class MyPageActivity extends AppCompatActivity {

    private View loggedInBox, loggedOutBox;
    private TextView nameText, emailText, joinText;
    private RecyclerView recentList;
    private TextView recentEmpty;
    private Button recentClear;
    private RecentAdapter recentAdapter;
    private Call<UserProfile> meCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_page);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.mypage_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        loggedInBox = findViewById(R.id.mypage_logged_in);
        loggedOutBox = findViewById(R.id.mypage_logged_out);
        nameText = findViewById(R.id.mypage_name);
        emailText = findViewById(R.id.mypage_email);
        joinText = findViewById(R.id.mypage_join);
        recentList = findViewById(R.id.mypage_recent_list);
        recentEmpty = findViewById(R.id.mypage_recent_empty);
        recentClear = findViewById(R.id.mypage_recent_clear);

        findViewById(R.id.mypage_login).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.mypage_alerts).setOnClickListener(v ->
                startActivity(new Intent(this, AlertListActivity.class)));
        findViewById(R.id.mypage_logout).setOnClickListener(v -> confirmLogout());
        recentClear.setOnClickListener(v -> confirmClearRecent());

        recentAdapter = new RecentAdapter(false, entry -> {
            Intent intent = new Intent(this, ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, entry.product);
            intent.putExtra(ProductDetailActivity.EXTRA_CATEGORY, entry.category);
            startActivity(intent);
        });
        recentList.setLayoutManager(new LinearLayoutManager(this));
        recentList.setNestedScrollingEnabled(false);   // 바깥 스크롤과 함께 움직이게
        recentList.setAdapter(recentAdapter);
    }

    /** 로그인 화면이나 리포트에서 돌아올 때마다 새로 그린다 */
    @Override
    protected void onResume() {
        super.onResume();
        bindAccount();
        bindRecent();
    }

    // ── 내 정보 ──────────────────────────────────────────────

    private void bindAccount() {
        if (!SessionManager.isLoggedIn()) {
            loggedInBox.setVisibility(View.GONE);
            loggedOutBox.setVisibility(View.VISIBLE);
            return;
        }
        loggedOutBox.setVisibility(View.GONE);
        loggedInBox.setVisibility(View.VISIBLE);

        // 저장해 둔 정보로 먼저 그리고, 서버에서 최신 정보를 받아 덮어쓴다
        nameText.setText(SessionManager.getName() + "님");
        emailText.setText(SessionManager.getEmail());
        joinText.setVisibility(View.GONE);

        if (meCall != null) meCall.cancel();
        meCall = ApiClient.get().getMe();
        meCall.enqueue(new Callback<UserProfile>() {
            @Override
            public void onResponse(@NonNull Call<UserProfile> call, @NonNull Response<UserProfile> response) {
                if (response.code() == 401) {          // 로그인 만료 → 로그아웃 상태로 보여줌
                    SessionManager.logout();
                    bindAccount();
                    return;
                }
                UserProfile me = response.body();
                if (!response.isSuccessful() || me == null) return;

                String name = me.nickname != null && !me.nickname.isEmpty() ? me.nickname : me.companyName;
                if (name != null && !name.isEmpty()) nameText.setText(name + "님");
                if (me.email != null) emailText.setText(me.email);

                String type = "b2b".equals(me.userType) ? "사업자 회원" : "일반 회원";
                String joined = formatJoinDate(me.createdAt);
                joinText.setText(joined != null ? type + " · " + joined + " 가입" : type);
                joinText.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<UserProfile> call, @NonNull Throwable t) {
                // 저장해 둔 정보가 이미 보이므로 따로 안내하지 않는다
            }
        });
    }

    /** "2026-11-14 10:20:30+00:00" → "2026.11.14" */
    private String formatJoinDate(String s) {
        if (s == null || s.length() < 10) return null;
        return s.substring(0, 4) + "." + s.substring(5, 7) + "." + s.substring(8, 10);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setMessage("로그아웃할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (d, w) -> {
                    SessionManager.logout();
                    Toast.makeText(this, "로그아웃했어요", Toast.LENGTH_SHORT).show();
                    bindAccount();
                })
                .show();
    }

    // ── F-08 최근 본 제품 ─────────────────────────────────────

    private void bindRecent() {
        List<RecentProductDb.Entry> entries = RecentProductDb.get(this).list(20);
        recentAdapter.setItems(entries);
        boolean empty = entries.isEmpty();
        recentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recentList.setVisibility(empty ? View.GONE : View.VISIBLE);
        recentClear.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void confirmClearRecent() {
        new AlertDialog.Builder(this)
                .setMessage("최근 본 제품 기록을 모두 지울까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("지우기", (d, w) -> {
                    RecentProductDb.get(this).clear();
                    bindRecent();
                })
                .show();
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
        if (meCall != null) meCall.cancel();
        super.onDestroy();
    }
}
