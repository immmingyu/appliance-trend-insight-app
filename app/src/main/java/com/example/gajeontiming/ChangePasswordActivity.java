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

import com.example.gajeontiming.model.AuthModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;
import com.google.android.material.appbar.MaterialToolbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 비밀번호 변경 (마이페이지 → 이메일 회원만).
 * POST /api/auth/password/change {current_password, new_password, new_password_confirm}
 * 지금 비밀번호가 틀리거나(401) 규칙에 안 맞으면(400) 서버 안내를 그대로 보여 준다.
 * 바꿔도 지금 로그인 토큰은 그대로 쓸 수 있어서 로그인을 다시 하지 않는다.
 */
public class ChangePasswordActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD = 8;   // 서버 · 회원가입과 같은 규칙

    private EditText currentInput, newInput, confirmInput;
    private Button submitButton;
    private ProgressBar progress;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.password_change_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentInput = findViewById(R.id.password_current);
        newInput = findViewById(R.id.password_new);
        confirmInput = findViewById(R.id.password_new_confirm);
        submitButton = findViewById(R.id.password_submit);
        progress = findViewById(R.id.password_progress);
        errorText = findViewById(R.id.password_error);

        submitButton.setOnClickListener(v -> submit());
    }

    private void submit() {
        String current = currentInput.getText().toString();
        String next = newInput.getText().toString();
        String confirm = confirmInput.getText().toString();

        // 서버에 보내기 전에 앱에서 먼저 확인 (서버도 같은 규칙으로 한 번 더 확인한다)
        if (current.isEmpty()) {
            showError("지금 비밀번호를 입력해 주세요.");
            return;
        }
        if (next.length() < MIN_PASSWORD) {
            showError("새 비밀번호는 " + MIN_PASSWORD + "자 이상으로 정해 주세요.");
            return;
        }
        if (!next.equals(confirm)) {
            showError("새 비밀번호가 서로 달라요.");
            return;
        }
        if (next.equals(current)) {
            showError("지금 비밀번호와 다른 비밀번호를 입력해 주세요.");
            return;
        }

        setLoading(true);
        ApiClient.get().changePassword(new AuthModels.PasswordChangeRequest(current, next, confirm))
                .enqueue(new Callback<AuthModels.MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.MessageResponse> call,
                                           @NonNull Response<AuthModels.MessageResponse> response) {
                        setLoading(false);
                        if (!response.isSuccessful()) {
                            showError(ApiErrors.message(response, "비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요."));
                            return;
                        }
                        AuthModels.MessageResponse body = response.body();
                        String msg = body != null && body.message != null ? body.message : "비밀번호를 바꿨어요";
                        Toast.makeText(ChangePasswordActivity.this, msg, Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.MessageResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        showError("인터넷 연결을 확인해 주세요.");
                    }
                });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
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
