package com.example.gajeontiming;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gajeontiming.alert.ApprovalCheckWorker;
import com.example.gajeontiming.alert.PushTokens;
import com.example.gajeontiming.auth.GoogleLogin;
import com.example.gajeontiming.auth.KakaoLogin;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.auth.SocialLoginCallback;
import com.example.gajeontiming.auth.SocialSession;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-10 로그인 화면.
 * 로그인에 성공하면 RESULT_OK로 닫혀서, 로그인이 필요해 이 화면을 연 쪽이 하던 일을 이어서 한다.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private ProgressBar progress;
    private TextView errorText;

    private View kakaoButton, googleButton;

    /** 회원가입 · 소셜 첫 가입에 성공하면 이미 로그인된 상태라 로그인 화면도 바로 닫는다 */
    private final ActivityResultLauncher<Intent> signUpLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    setResult(RESULT_OK);
                    finish();
                } else if (result.getResultCode() == SignUpActivity.RESULT_PENDING && result.getData() != null) {
                    // 사업자 가입 신청 후: 승인되면 바로 로그인할 수 있게 이메일을 채워 둔다
                    emailInput.setText(result.getData().getStringExtra(SignUpActivity.EXTRA_EMAIL));
                    passwordInput.requestFocus();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.login_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        emailInput = findViewById(R.id.login_email);
        passwordInput = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        progress = findViewById(R.id.login_progress);
        errorText = findViewById(R.id.login_error);

        loginButton.setOnClickListener(v -> login());
        findViewById(R.id.login_signup).setOnClickListener(v ->
                signUpLauncher.launch(new Intent(this, SignUpActivity.class)));

        kakaoButton = findViewById(R.id.login_kakao);
        kakaoButton.setOnClickListener(v -> loginWithKakao());
        kakaoButton.setVisibility(KakaoLogin.isConfigured() ? View.VISIBLE : View.GONE);
        googleButton = findViewById(R.id.login_google);
        googleButton.setOnClickListener(v -> loginWithGoogle());
        googleButton.setVisibility(GoogleLogin.isConfigured() ? View.VISIBLE : View.GONE);
        boolean anySocial = KakaoLogin.isConfigured() || GoogleLogin.isConfigured();
        findViewById(R.id.login_social_box).setVisibility(anySocial ? View.VISIBLE : View.GONE);
    }

    // ── 소셜 로그인 (카카오 · Google) ──────────────────────────

    /**
     * SDK로 토큰을 받아 서버에 보낸다 → 기존 회원이면 로그인, 처음이면 가입 마무리 화면.
     *   카카오: 카카오 SDK 액세스 토큰 / Google: Credential Manager ID 토큰
     */
    private void loginWithKakao() {
        setLoading(true);
        KakaoLogin.login(this, socialCallback("kakao"));
    }

    private void loginWithGoogle() {
        setLoading(true);
        GoogleLogin.login(this, socialCallback("google"));
    }

    private SocialLoginCallback socialCallback(String provider) {
        return new SocialLoginCallback() {
            @Override
            public void onToken(String token) {
                sendSocialToken(provider, token);
            }

            @Override
            public void onCancel() {
                setLoading(false);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        };
    }

    private void sendSocialToken(String provider, String sdkToken) {
        ApiClient.get().socialToken(new AuthModels.SocialTokenRequest(provider, sdkToken))
                .enqueue(new Callback<AuthModels.SocialTokenResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.SocialTokenResponse> call,
                                           @NonNull Response<AuthModels.SocialTokenResponse> response) {
                        AuthModels.SocialTokenResponse body = response.body();
                        if (!response.isSuccessful() || body == null) {
                            setLoading(false);
                            // 401 토큰 검증 실패 · 403 계정 상태(승인 대기 등)는 서버 안내를 그대로
                            showError(ApiErrors.message(response, "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."));
                            return;
                        }
                        if (body.token != null) {             // 기존 회원
                            SocialSession.start(body.token, provider, null, () -> {
                                setLoading(false);
                                PushTokens.register();
                                Toast.makeText(LoginActivity.this,
                                        SessionManager.getName() + "님, 반가워요", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            });
                        } else if (body.signup != null) {     // 처음 온 사람 → 닉네임 · 약관 동의
                            setLoading(false);
                            Intent intent = new Intent(LoginActivity.this, SocialSignupActivity.class);
                            intent.putExtra(SocialSignupActivity.EXTRA_TICKET, body.signup);
                            intent.putExtra(SocialSignupActivity.EXTRA_PROVIDER, provider);
                            intent.putExtra(SocialSignupActivity.EXTRA_NAME, body.name);
                            signUpLauncher.launch(intent);
                        } else {
                            setLoading(false);
                            showError("로그인하지 못했어요. 잠시 후 다시 시도해 주세요.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.SocialTokenResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    private void login() {
        String email = emailInput.getText().toString().trim().toLowerCase();
        String password = passwordInput.getText().toString();

        // 서버에 보내기 전에 앱에서 먼저 확인
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("이메일 주소를 확인해 주세요.");
            return;
        }
        if (password.isEmpty()) {
            showError("비밀번호를 입력해 주세요.");
            return;
        }

        setLoading(true);
        ApiClient.get().login(new AuthModels.LoginRequest(email, password))
                .enqueue(new Callback<AuthModels.AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.AuthResponse> call,
                                           @NonNull Response<AuthModels.AuthResponse> response) {
                        setLoading(false);
                        AuthModels.AuthResponse body = response.body();
                        if (response.isSuccessful() && body != null && body.token != null) {
                            String name = body.nickname != null ? body.nickname : body.companyName;
                            SessionManager.save(body.token, email, name);
                            SessionManager.saveAccount(body.userType, body.status, body.tier, body.tierExpiresAt);
                            SessionManager.saveHasPassword(true);   // 이메일 · 비밀번호로 들어왔다
                            ApprovalCheckWorker.sync(LoginActivity.this);   // 승인 대기 사업자면 승인 확인 예약
                            PushTokens.register();                          // 푸시 받을 휴대폰 등록
                            Toast.makeText(LoginActivity.this, welcomeMessage(), Toast.LENGTH_LONG).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            // 오류 본문은 한 번만 읽을 수 있어서 먼저 꺼내 둔다
                            String msg = ApiErrors.message(response, "로그인하지 못했어요. 잠시 후 다시 시도해 주세요.");
                            if ("PENDING".equals(msg)) {        // 사업자 회원 승인 전 (403)
                                showError("사업자 회원 승인을 기다리는 중이에요.");
                                startActivity(PendingApprovalActivity.onLogin(LoginActivity.this, email));
                            } else {
                                showError(msg);                 // 거절(403) 등은 서버 안내 그대로
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.AuthResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    /** 로그인 인사. 승인 대기 · 거절 사업자는 상태를 함께 알려 준다 (개인 기능은 그대로 쓸 수 있음) */
    private String welcomeMessage() {
        String hello = SessionManager.getName() + "님, 반가워요";
        if (SessionManager.isB2bPending()) return hello + "\n" + getString(R.string.b2b_pending_short);
        if (SessionManager.isB2bRejected()) return hello + "\n" + getString(R.string.b2b_rejected);
        // 승인 대기 중에 신청하고 승인 뒤 처음 로그인한 경우도 여기서 알 수 있게
        if (SessionManager.isB2b()) return hello + "\n" + getString(R.string.b2b_approved_message);
        return hello;
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        if (kakaoButton != null) kakaoButton.setEnabled(!loading);
        if (googleButton != null) googleButton.setEnabled(!loading);
        if (loading) errorText.setVisibility(View.GONE);
    }

    private void showError(String text) {
        errorText.setText(text);
        errorText.setVisibility(View.VISIBLE);
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
