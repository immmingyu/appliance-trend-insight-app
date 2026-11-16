package com.example.gajeontiming;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-09 회원가입 화면. 세 단계로 진행한다.
 *   1단계: 이메일 입력 → 인증번호 받기
 *   2단계: 메일로 온 6자리 인증번호 확인 (10분 안에)
 *   3단계: 비밀번호 · 닉네임 입력 → 가입 (가입하면 바로 로그인 상태)
 * 앞 단계를 마쳐야 다음 단계 입력칸이 열린다.
 */
public class SignUpActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD = 8;
    private static final long CODE_VALID_MS = 10 * 60 * 1000;   // 서버의 인증번호 유효 시간

    private EditText emailInput, codeInput, passwordInput, passwordConfirmInput, nicknameInput;
    private Button sendCodeButton, verifyButton, signUpButton;
    private View codeStep, accountStep;
    private TextView codeTimer, errorText;
    private ProgressBar progress;

    private String verifiedEmail;    // 인증을 마친 이메일 (이후 이메일을 바꾸면 처음부터)
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.signup_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        emailInput = findViewById(R.id.signup_email);
        codeInput = findViewById(R.id.signup_code);
        passwordInput = findViewById(R.id.signup_password);
        passwordConfirmInput = findViewById(R.id.signup_password_confirm);
        nicknameInput = findViewById(R.id.signup_nickname);
        sendCodeButton = findViewById(R.id.signup_send_code);
        verifyButton = findViewById(R.id.signup_verify);
        signUpButton = findViewById(R.id.signup_button);
        codeStep = findViewById(R.id.signup_code_step);
        accountStep = findViewById(R.id.signup_account_step);
        codeTimer = findViewById(R.id.signup_code_timer);
        errorText = findViewById(R.id.signup_error);
        progress = findViewById(R.id.signup_progress);

        sendCodeButton.setOnClickListener(v -> sendCode());
        verifyButton.setOnClickListener(v -> verifyCode());
        signUpButton.setOnClickListener(v -> signUp());

        codeStep.setVisibility(View.GONE);
        accountStep.setVisibility(View.GONE);
    }

    private String email() {
        return emailInput.getText().toString().trim().toLowerCase();
    }

    // ── 1단계: 인증번호 받기 ─────────────────────────────────

    private void sendCode() {
        String email = email();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("이메일 주소를 확인해 주세요.");
            return;
        }
        setLoading(true);
        ApiClient.get().sendCode(new AuthModels.EmailRequest(email))
                .enqueue(new Callback<AuthModels.MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.MessageResponse> call,
                                           @NonNull Response<AuthModels.MessageResponse> response) {
                        setLoading(false);
                        if (!response.isSuccessful()) {
                            showError(ApiErrors.message(response, "인증번호를 보내지 못했어요."));
                            return;
                        }
                        verifiedEmail = null;
                        accountStep.setVisibility(View.GONE);
                        codeStep.setVisibility(View.VISIBLE);
                        codeInput.setText("");
                        codeInput.requestFocus();
                        sendCodeButton.setText(R.string.signup_resend);
                        startTimer();
                        Toast.makeText(SignUpActivity.this,
                                "인증번호를 보냈어요. 메일함을 확인해 주세요", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    /** 남은 시간 표시 (10:00 → 0:00) */
    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(CODE_VALID_MS, 1000) {
            @Override
            public void onTick(long left) {
                long sec = left / 1000;
                codeTimer.setText(String.format(Locale.KOREA, "%d:%02d", sec / 60, sec % 60));
            }

            @Override
            public void onFinish() {
                codeTimer.setText("만료됨");
            }
        }.start();
    }

    // ── 2단계: 인증번호 확인 ─────────────────────────────────

    private void verifyCode() {
        String email = email();
        String code = codeInput.getText().toString().trim();
        if (code.length() != 6) {
            showError("메일로 받은 6자리 숫자를 입력해 주세요.");
            return;
        }
        setLoading(true);
        ApiClient.get().verifyCode(new AuthModels.VerifyRequest(email, code))
                .enqueue(new Callback<AuthModels.MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.MessageResponse> call,
                                           @NonNull Response<AuthModels.MessageResponse> response) {
                        setLoading(false);
                        if (!response.isSuccessful()) {
                            showError(ApiErrors.message(response, "인증번호를 확인하지 못했어요."));
                            return;
                        }
                        verifiedEmail = email;
                        if (timer != null) timer.cancel();
                        codeTimer.setText("인증 완료");
                        // 인증한 이메일은 더 못 바꾸게 잠근다
                        emailInput.setEnabled(false);
                        sendCodeButton.setEnabled(false);
                        codeInput.setEnabled(false);
                        verifyButton.setEnabled(false);
                        accountStep.setVisibility(View.VISIBLE);
                        passwordInput.requestFocus();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    // ── 3단계: 가입 ─────────────────────────────────────────

    private void signUp() {
        if (verifiedEmail == null) {
            showError("이메일 인증을 먼저 해 주세요.");
            return;
        }
        String password = passwordInput.getText().toString();
        String confirm = passwordConfirmInput.getText().toString();
        String nickname = nicknameInput.getText().toString().trim();

        if (password.length() < MIN_PASSWORD) {
            showError("비밀번호는 " + MIN_PASSWORD + "자 이상으로 정해 주세요.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("비밀번호 확인이 일치하지 않아요.");
            return;
        }
        if (nickname.isEmpty() || nickname.length() > 20) {
            showError("닉네임을 1~20자로 입력해 주세요.");
            return;
        }

        setLoading(true);
        ApiClient.get().signup(new AuthModels.SignupRequest(verifiedEmail, password, nickname))
                .enqueue(new Callback<AuthModels.AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.AuthResponse> call,
                                           @NonNull Response<AuthModels.AuthResponse> response) {
                        setLoading(false);
                        AuthModels.AuthResponse body = response.body();
                        if (!response.isSuccessful() || body == null || body.token == null) {
                            showError(ApiErrors.message(response, "가입하지 못했어요."));
                            return;
                        }
                        SessionManager.save(body.token, verifiedEmail, body.nickname);
                        Toast.makeText(SignUpActivity.this,
                                nickname + "님, 가입을 환영해요", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.AuthResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    // ── 공통 ───────────────────────────────────────────────

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
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

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }
}
