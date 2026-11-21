package com.example.gajeontiming;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.adapter.RecentAdapter;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.data.RecentProductDb;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.model.UserProfile;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-15 마이페이지.
 * 위: 내 정보(로그인했을 때) 또는 로그인 안내 / 가운데: F-08 최근 본 제품 전체 목록
 * 아래: 앱 정보(문의 · 개인정보처리방침 · 이용약관 · 오픈소스 라이선스 · 버전) · 회원 탈퇴.
 * 주소 · 연락처는 AppLinks에 모아 두었다.
 * 로그인 · 회원가입 화면은 이 화면 아래 단계에 있다 (홈 → 마이페이지 → 로그인 → 회원가입).
 */
public class MyPageActivity extends AppCompatActivity {

    private View loggedInBox, loggedOutBox;
    private TextView nameText, emailText, joinText;
    private RecyclerView recentList;
    private TextView recentEmpty;
    private Button recentClear;
    private Button withdrawButton;
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
        withdrawButton = findViewById(R.id.mypage_withdraw);
        withdrawButton.setOnClickListener(v -> onWithdraw());
        setupInfo();

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
        boolean loggedIn = SessionManager.isLoggedIn();
        withdrawButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (!loggedIn) {
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

    // ── 앱 정보 ───────────────────────────────────────────────

    private void setupInfo() {
        infoRow(R.id.mypage_contact, R.string.mypage_contact, null, v -> openContact());
        infoRow(R.id.mypage_privacy, R.string.mypage_privacy, null, v -> openUrl(AppLinks.PRIVACY_URL));
        infoRow(R.id.mypage_terms, R.string.mypage_terms, null, v -> openUrl(AppLinks.TERMS_URL));
        infoRow(R.id.mypage_licenses, R.string.mypage_licenses, null, v -> showLicenses());
        infoRow(R.id.mypage_version, R.string.mypage_version, versionName(), null);
    }

    /** item_setting_row 한 줄에 이름 · 값 · 누를 때 동작을 넣는다 (동작이 없으면 눌리지 않게) */
    private void infoRow(int rowId, int label, String value, View.OnClickListener click) {
        View row = findViewById(rowId);
        ((TextView) row.findViewById(R.id.setting_label)).setText(label);
        TextView valueText = row.findViewById(R.id.setting_value);
        if (value != null) {
            valueText.setText(value);
            valueText.setVisibility(View.VISIBLE);
        }
        if (click != null) {
            row.setOnClickListener(click);
        } else {
            row.setClickable(false);
            row.setBackground(null);
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void openUrl(String url) {
        if (url == null) {
            notReady();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    /** 이메일 앱으로 문의 메일 쓰기 (받는 사람 · 제목 · 앱 버전을 미리 채운다) */
    private void openContact() {
        if (AppLinks.SUPPORT_EMAIL == null) {
            notReady();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{AppLinks.SUPPORT_EMAIL});
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.mypage_contact_subject));
        intent.putExtra(Intent.EXTRA_TEXT, "\n\n---\n앱 버전 " + versionName()
                + " · Android " + android.os.Build.VERSION.RELEASE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "메일 앱이 없어요. " + AppLinks.SUPPORT_EMAIL + "로 보내 주세요",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showLicenses() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.mypage_licenses)
                .setMessage(R.string.oss_licenses)
                .setPositiveButton("닫기", null)
                .show();
    }

    // ── 회원 탈퇴 ─────────────────────────────────────────────

    /**
     * 확인 창에서 비밀번호를 받아 POST /api/auth/withdraw.
     * 비밀번호가 틀리면 창을 닫지 않고 서버 안내("비밀번호가 올바르지 않습니다")를 창 안에 보여 준다.
     */
    private void onWithdraw() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_withdraw, null);
        EditText password = view.findViewById(R.id.withdraw_password);
        TextView error = view.findViewById(R.id.withdraw_error);
        ProgressBar progress = view.findViewById(R.id.withdraw_progress);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.withdraw_title)
                .setView(view)
                .setNegativeButton("취소", null)
                .setPositiveButton(R.string.withdraw_confirm, null)   // 눌러도 바로 닫히지 않게 아래에서 따로 연결
                .create();
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setTextColor(ContextCompat.getColor(this, R.color.score_red));
            confirm.setOnClickListener(v -> {
                String pw = password.getText().toString();
                if (pw.isEmpty()) {
                    error.setText("비밀번호를 입력해 주세요.");
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                error.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
                confirm.setEnabled(false);
                ApiClient.get().withdraw(new AuthModels.WithdrawRequest(pw))
                        .enqueue(new Callback<AuthModels.MessageResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<AuthModels.MessageResponse> call,
                                                   @NonNull Response<AuthModels.MessageResponse> response) {
                                progress.setVisibility(View.GONE);
                                confirm.setEnabled(true);
                                if (!response.isSuccessful()) {
                                    error.setText(ApiErrors.message(response, "탈퇴하지 못했어요. 잠시 후 다시 시도해 주세요."));
                                    error.setVisibility(View.VISIBLE);
                                    return;
                                }
                                dialog.dismiss();
                                onWithdrawn(response.body());
                            }

                            @Override
                            public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                                progress.setVisibility(View.GONE);
                                confirm.setEnabled(true);
                                error.setText("인터넷 연결을 확인해 주세요.");
                                error.setVisibility(View.VISIBLE);
                            }
                        });
            });
        });
        dialog.show();
    }

    /** 탈퇴 완료: 로그인 정보와 이 계정의 알림 기록을 지우고 로그아웃 상태로 다시 그린다 */
    private void onWithdrawn(AuthModels.MessageResponse body) {
        SessionManager.logout();
        // 가격 알림 "마지막으로 알린 가격" 기록 (PriceCheckWorker). 알림이 서버에서 지워졌으니 같이 비운다
        getSharedPreferences("alert_notified", MODE_PRIVATE).edit().clear().apply();
        String msg = body != null && body.message != null ? body.message : "탈퇴가 완료되었어요";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        bindAccount();
    }

    private void notReady() {
        Toast.makeText(this, R.string.mypage_not_ready, Toast.LENGTH_SHORT).show();
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
