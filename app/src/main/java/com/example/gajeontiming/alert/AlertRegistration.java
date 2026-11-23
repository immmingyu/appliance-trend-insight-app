package com.example.gajeontiming.alert;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.gajeontiming.LoginActivity;
import com.example.gajeontiming.R;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.model.AlertModels;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.network.ApiErrors;

import java.text.NumberFormat;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-11 가격 알림 등록 흐름 (제품 상세 · 구매 타이밍 화면이 같이 쓴다).
 * 로그인 확인 → 목표 가격 입력 창 → POST /api/user/alerts → 안드로이드 13 이상이면 알림 권한 요청.
 * 로그인 · 권한 요청 창을 쓰려면 화면이 만들어질 때 등록해야 해서, 화면의 필드로 만든다:
 *   private final AlertRegistration alertRegistration = new AlertRegistration(this);
 */
public final class AlertRegistration {

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<Intent> loginLauncher;
    private final ActivityResultLauncher<String> permissionLauncher;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    // 로그인하고 돌아오면 이어서 띄울 등록 창 정보
    private String name, link;
    private long current, low;

    public AlertRegistration(AppCompatActivity activity) {
        this.activity = activity;
        loginLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && name != null) showDialog();
                });
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });
    }

    /**
     * @param productName 서버에 저장할 제품명 (알림 목록 · 푸시에 그대로 나온다)
     * @param link        판매 페이지 (알림 목록에서 누르면 열림)
     * @param currentPrice 지금 가격 (목표 가격은 이보다 낮아야 한다)
     * @param lowPrice    최근 최저가 (목표 가격 기본값 계산용, 모르면 0)
     */
    public void start(String productName, String link, long currentPrice, long lowPrice) {
        if (currentPrice <= 0) return;
        this.name = productName;
        this.link = link;
        this.current = currentPrice;
        this.low = lowPrice;
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(activity, "가격 알림은 로그인 후에 쓸 수 있어요", Toast.LENGTH_SHORT).show();
            loginLauncher.launch(new Intent(activity, LoginActivity.class));
            return;
        }
        showDialog();
    }

    /** 목표 가격을 입력받는 창. 기본값은 최근 최저가와 현재가의 95% 중 낮은 값 (천 원 단위) */
    private void showDialog() {
        long suggest = Math.min(low > 0 ? low : current, Math.round(current * 0.95));
        suggest = suggest / 1000 * 1000;

        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(suggest));
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(activity);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.alert_dialog_title)
                .setMessage(activity.getString(R.string.alert_dialog_message, won.format(current)))
                .setView(box)
                .setNegativeButton("취소", null)
                .setPositiveButton("등록", (d, w) -> {
                    long target;
                    try {
                        target = Long.parseLong(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        target = 0;
                    }
                    if (target <= 0) {
                        toast("목표 가격을 숫자로 입력해 주세요");
                    } else if (target >= current) {
                        toast("지금 가격(" + won.format(current) + "원)보다 낮게 정해 주세요");
                    } else {
                        create(target);
                    }
                })
                .show();
    }

    private void create(long target) {
        AlertModels.CreateRequest req = new AlertModels.CreateRequest(name, target, current, link);
        ApiClient.get().createAlert(req).enqueue(new Callback<AlertModels.CreateResponse>() {
            @Override
            public void onResponse(@NonNull Call<AlertModels.CreateResponse> call,
                                   @NonNull Response<AlertModels.CreateResponse> response) {
                if (activity.isFinishing()) return;
                if (response.code() == 401) {                 // 로그인 만료 → 로그인 후 다시 등록 창
                    SessionManager.logout();
                    loginLauncher.launch(new Intent(activity, LoginActivity.class));
                    return;
                }
                if (!response.isSuccessful()) {           // 409 이미 등록 · 400 개수 제한 등은 서버 문구 그대로
                    toast(ApiErrors.message(response, "알림을 등록하지 못했어요"));
                    return;
                }
                Toast.makeText(activity, won.format(target) + "원 이하가 되면 알려드릴게요",
                        Toast.LENGTH_LONG).show();
                // 안드로이드 13 이상은 알림 권한이 있어야 알림이 뜬다
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AlertModels.CreateResponse> call, @NonNull Throwable t) {
                if (!activity.isFinishing()) toast("인터넷 연결을 확인해 주세요");
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
