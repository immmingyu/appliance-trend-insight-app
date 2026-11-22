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

import com.example.gajeontiming.alert.ApprovalCheckWorker;
import com.example.gajeontiming.alert.PushTokens;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.auth.TermsAgreement;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import android.content.Intent;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-09 회원가입 화면. 세 단계로 진행한다.
 *   1단계: 이메일 입력 → 인증번호 받기
 *   2단계: 메일로 온 6자리 인증번호 확인 (10분 안에)
 *   맨 위: 회원 유형 선택 (개인 · 사업자)
 *   3단계: 비밀번호 · 닉네임 입력 · 약관 동의 → 가입 (가입하면 바로 로그인 상태)
 *          사업자: 비밀번호 · 회사명 · 업종 · 연락처(선택) → 가입 신청 → 승인 대기 화면 (로그인은 승인 후)
 *          이용약관 · 개인정보 수집 · 이용 동의(둘 다 필수)를 체크해야 가입 버튼이 켜진다
 * 앞 단계를 마쳐야 다음 단계 입력칸이 열린다.
 */
public class SignUpActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD = 8;
    private static final long CODE_VALID_MS = 10 * 60 * 1000;   // 서버의 인증번호 유효 시간

    /** 사업자 가입 신청이 끝나면 이 결과로 닫힌다 (로그인은 되지 않음, data에 이메일) */
    public static final int RESULT_PENDING = RESULT_FIRST_USER;
    public static final String EXTRA_EMAIL = "email";

    private EditText emailInput, codeInput, passwordInput, passwordConfirmInput, nicknameInput;
    private EditText companyInput, businessEtcInput, contactPhoneInput;
    private MaterialAutoCompleteTextView businessTypeInput;
    private View businessBox, businessNotice;
    private boolean business;        // 사업자 회원으로 가입하는지
    private Button sendCodeButton, verifyButton, signUpButton;
    private View codeStep, accountStep;
    private TextView codeTimer, errorText;
    private ProgressBar progress;
    private TermsAgreement agreement;

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

        companyInput = findViewById(R.id.signup_company);
        businessTypeInput = findViewById(R.id.signup_business_type);
        businessEtcInput = findViewById(R.id.signup_business_etc);
        contactPhoneInput = findViewById(R.id.signup_contact_phone);
        businessBox = findViewById(R.id.signup_business_box);
        businessNotice = findViewById(R.id.signup_business_notice);
        setupMemberType();

        sendCodeButton.setOnClickListener(v -> sendCode());
        verifyButton.setOnClickListener(v -> verifyCode());
        signUpButton.setOnClickListener(v -> signUp());
        // 두 필수 약관에 모두 동의해야 가입 버튼이 켜진다
        agreement = new TermsAgreement(this, () -> signUpButton.setEnabled(agreement.allAgreed()));
        signUpButton.setEnabled(false);

        codeStep.setVisibility(View.GONE);
        accountStep.setVisibility(View.GONE);
    }

    // ── 회원 유형: 개인 · 사업자 ─────────────────────────────

    private void setupMemberType() {
        MaterialButtonToggleGroup group = findViewById(R.id.signup_type_group);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) setBusiness(id == R.id.signup_type_business);
        });
        // 업종 "기타"를 고르면 직접 입력칸을 연다
        businessTypeInput.setOnItemClickListener((parent, view, position, id) -> {
            boolean other = isOtherType(businessTypeInput.getText().toString());
            businessEtcInput.setVisibility(other ? View.VISIBLE : View.GONE);
            if (other) businessEtcInput.requestFocus();
        });
        setBusiness(false);
    }

    private void setBusiness(boolean business) {
        this.business = business;
        businessNotice.setVisibility(business ? View.VISIBLE : View.GONE);
        businessBox.setVisibility(business ? View.VISIBLE : View.GONE);
        nicknameInput.setVisibility(business ? View.GONE : View.VISIBLE);
        signUpButton.setText(business ? R.string.signup_submit_business : R.string.signup_submit);
        errorText.setVisibility(View.GONE);
    }

    private boolean isOtherType(String type) {
        return getString(R.string.business_type_other).equals(type);
    }

    /** 서버에 보낼 업종: 목록 항목 이름 그대로, "기타"면 직접 입력한 글자. 아직 없으면 빈 문자열 */
    private String businessType() {
        String selected = businessTypeInput.getText().toString().trim();
        if (isOtherType(selected)) return businessEtcInput.getText().toString().trim();
        return selected;
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
        if (!agreement.allAgreed()) {
            showError("이용약관과 개인정보 수집 · 이용에 동의해 주세요.");
            return;
        }
        if (business) {
            signUpBusiness(password);
            return;
        }
        if (nickname.isEmpty() || nickname.length() > 20) {
            showError("닉네임을 1~20자로 입력해 주세요.");
            return;
        }

        setLoading(true);
        ApiClient.get().signup(new AuthModels.SignupRequest(verifiedEmail, password, nickname, true))
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
                        SessionManager.saveAccount("b2c", "active", null, null);   // 개인 회원은 가입 즉시 사용
                        SessionManager.saveHasPassword(true);
                        PushTokens.register();
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

    /** 사업자 가입 신청 → 승인 대기 화면 (로그인은 관리자 승인 후) */
    private void signUpBusiness(String password) {
        String company = companyInput.getText().toString().trim();
        String selected = businessTypeInput.getText().toString().trim();
        String type = businessType();
        String phone = contactPhoneInput.getText().toString().trim();

        if (company.isEmpty()) {
            showError("회사명을 입력해 주세요.");
            return;
        }
        if (selected.isEmpty()) {
            showError("업종을 골라 주세요.");
            return;
        }
        if (type.isEmpty()) {   // "기타"를 골랐는데 비어 있음
            showError("업종을 직접 입력해 주세요.");
            return;
        }

        setLoading(true);
        signUpButton.setEnabled(false);
        ApiClient.get().signupB2b(new AuthModels.SignupB2bRequest(verifiedEmail, password, company, type, phone, true))
                .enqueue(new Callback<AuthModels.SignupB2bResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.SignupB2bResponse> call,
                                           @NonNull Response<AuthModels.SignupB2bResponse> response) {
                        setLoading(false);
                        signUpButton.setEnabled(agreement.allAgreed());
                        if (!response.isSuccessful()) {
                            // 400 회사명 · 업종 누락, 409 이미 가입된 이메일 등은 서버 안내 그대로
                            showError(ApiErrors.message(response, "가입 신청을 하지 못했어요."));
                            return;
                        }
                        AuthModels.SignupB2bResponse body = response.body();
                        boolean loggedIn = body != null && body.token != null;
                        if (loggedIn) {
                            // 승인 전에도 개인 기능은 쓸 수 있게 바로 로그인 (비즈니스 메뉴는 승인 후)
                            SessionManager.save(body.token, verifiedEmail, company);
                            SessionManager.saveAccount("b2b", body.status != null ? body.status : "pending",
                                    body.tier, body.tierExpiresAt);
                            SessionManager.saveHasPassword(true);
                            ApprovalCheckWorker.sync(SignUpActivity.this);   // 푸시가 안 올 때를 대비한 주기적 확인
                            PushTokens.register();                           // 승인 · 거절되면 바로 푸시
                        }
                        startActivity(PendingApprovalActivity.afterSignup(SignUpActivity.this, company,
                                verifiedEmail, loggedIn));
                        if (loggedIn) {
                            setResult(RESULT_OK);
                        } else {
                            // 예전 서버(토큰 없음): 로그인 화면에 이메일만 채워 준다
                            setResult(RESULT_PENDING, new Intent().putExtra(EXTRA_EMAIL, verifiedEmail));
                        }
                        finish();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.SignupB2bResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        signUpButton.setEnabled(agreement.allAgreed());
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
