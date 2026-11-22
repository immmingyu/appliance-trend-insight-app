package com.example.gajeontiming;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * 사업자 회원 승인 대기 안내 (B2B 단계 1).
 *   가입 신청 직후 (SignUpActivity)  → "가입 신청이 접수됐어요"
 *   승인 전에 로그인 (예전 서버 403 "PENDING") → "승인을 기다리고 있어요"
 * 지금 서버는 가입 신청과 함께 토큰을 줘서 승인 전에도 개인 기능은 쓸 수 있다 (문구로 안내).
 * 승인 · 거절은 웹 관리자 화면에서 하고, 결과는 서버가 이메일로 보낸다. 이 화면은 안내만 한다.
 */
public class PendingApprovalActivity extends AppCompatActivity {

    /** 승인 결과를 휴대폰 알림으로 받으려면 안드로이드 13부터 권한이 필요하다 (거절해도 앱 안 안내는 그대로) */
    private final ActivityResultLauncher<String> notifyPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });

    private static final String EXTRA_FROM_SIGNUP = "from_signup";
    private static final String EXTRA_COMPANY = "company";
    private static final String EXTRA_EMAIL = "email";
    private static final String EXTRA_LOGGED_IN = "logged_in";

    /** loggedIn: 서버가 가입과 함께 토큰을 줘서 이미 로그인됨 (승인 전에도 개인 기능 사용 가능) */
    public static Intent afterSignup(Context context, String company, String email, boolean loggedIn) {
        return new Intent(context, PendingApprovalActivity.class)
                .putExtra(EXTRA_FROM_SIGNUP, true)
                .putExtra(EXTRA_COMPANY, company)
                .putExtra(EXTRA_EMAIL, email)
                .putExtra(EXTRA_LOGGED_IN, loggedIn);
    }

    public static Intent onLogin(Context context, String email) {
        return new Intent(context, PendingApprovalActivity.class)
                .putExtra(EXTRA_FROM_SIGNUP, false)
                .putExtra(EXTRA_EMAIL, email);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approval);

        boolean fromSignup = getIntent().getBooleanExtra(EXTRA_FROM_SIGNUP, false);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.signup_type_business);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ((TextView) findViewById(R.id.pending_title)).setText(
                fromSignup ? R.string.pending_title_signed_up : R.string.pending_title_login);
        if (getIntent().getBooleanExtra(EXTRA_LOGGED_IN, false)) {
            ((TextView) findViewById(R.id.pending_body)).setText(R.string.pending_body_logged_in);
            askNotificationPermission();   // 승인 · 거절되면 휴대폰 알림 (ApprovalCheckWorker)
        }
        infoRow(R.id.pending_company_row, R.string.pending_company, getIntent().getStringExtra(EXTRA_COMPANY));
        infoRow(R.id.pending_email_row, R.string.pending_email, getIntent().getStringExtra(EXTRA_EMAIL));
        findViewById(R.id.pending_ok).setOnClickListener(v -> finish());
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void infoRow(int rowId, int label, String value) {
        View row = findViewById(rowId);
        if (value == null || value.isEmpty()) {
            row.setVisibility(View.GONE);
            return;
        }
        ((TextView) row.findViewById(R.id.setting_label)).setText(label);
        TextView valueText = row.findViewById(R.id.setting_value);
        valueText.setText(value);
        valueText.setVisibility(View.VISIBLE);
        row.setBackground(null);   // 누르는 줄이 아니라 정보만 보여 준다
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
