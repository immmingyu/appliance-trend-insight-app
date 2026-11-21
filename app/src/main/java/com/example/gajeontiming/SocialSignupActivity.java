package com.example.gajeontiming;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gajeontiming.auth.SocialSession;
import com.example.gajeontiming.auth.TermsAgreement;
import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 소셜 로그인으로 처음 온 사람의 가입 마무리.
 * 서버가 준 가입 티켓(15분) + 닉네임 + 약관 동의를 POST /api/auth/social/complete 로 보내면 가입 · 로그인된다.
 * 티켓이 만료되면 서버 안내("가입 시간이 지났어요…")를 보여 주고, 사용자는 뒤로 가서 다시 로그인하면 된다.
 * 성공하면 RESULT_OK로 닫혀서 로그인 화면도 같이 닫힌다.
 */
public class SocialSignupActivity extends AppCompatActivity {

    public static final String EXTRA_TICKET = "ticket";
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_NAME = "name";

    private String ticket, provider;
    private EditText nicknameInput;
    private Button submitButton;
    private TextView errorText;
    private ProgressBar progress;
    private TermsAgreement agreement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_social_signup);

        ticket = getIntent().getStringExtra(EXTRA_TICKET);
        provider = getIntent().getStringExtra(EXTRA_PROVIDER);
        String name = getIntent().getStringExtra(EXTRA_NAME);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.social_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ((TextView) findViewById(R.id.social_intro)).setText(
                getString(R.string.social_intro, providerLabel(provider)));
        nicknameInput = findViewById(R.id.social_nickname);
        if (name != null && name.length() <= 20) nicknameInput.setText(name);   // 카카오 이름을 기본값으로
        submitButton = findViewById(R.id.social_submit);
        errorText = findViewById(R.id.social_error);
        progress = findViewById(R.id.social_progress);

        agreement = new TermsAgreement(this, () -> submitButton.setEnabled(agreement.allAgreed()));
        submitButton.setEnabled(false);
        submitButton.setOnClickListener(v -> submit());
    }

    static String providerLabel(String provider) {
        if ("kakao".equals(provider)) return "카카오";
        if ("google".equals(provider)) return "Google";
        return "소셜";
    }

    private void submit() {
        String nickname = nicknameInput.getText().toString().trim();
        if (nickname.isEmpty() || nickname.length() > 20) {
            showError("닉네임을 1~20자로 입력해 주세요.");
            return;
        }
        if (!agreement.allAgreed()) {
            showError("이용약관과 개인정보 수집 · 이용에 동의해 주세요.");
            return;
        }
        setLoading(true);
        ApiClient.get().socialComplete(new AuthModels.SocialCompleteRequest(ticket, true, nickname))
                .enqueue(new Callback<AuthModels.AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.AuthResponse> call,
                                           @NonNull Response<AuthModels.AuthResponse> response) {
                        AuthModels.AuthResponse body = response.body();
                        if (!response.isSuccessful()) {
                            setLoading(false);
                            // 티켓 만료 · 동의 누락 등은 서버 안내를 그대로 보여 준다
                            showError(ApiErrors.message(response, "가입하지 못했어요. 잠시 후 다시 시도해 주세요."));
                            return;
                        }
                        if (body == null || body.token == null) {   // 성공 응답인데 토큰이 없음 (서버 응답 형식 문제)
                            setLoading(false);
                            showError("가입 응답을 읽지 못했어요. 잠시 후 다시 시도해 주세요. (토큰 없음)");
                            return;
                        }
                        SocialSession.start(body.token, provider, nickname, () -> {
                            Toast.makeText(SocialSignupActivity.this,
                                    nickname + "님, 가입을 환영해요", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.AuthResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading && agreement.allAgreed());
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
