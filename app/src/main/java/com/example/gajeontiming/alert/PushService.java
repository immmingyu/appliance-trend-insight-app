package com.example.gajeontiming.alert;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.gajeontiming.auth.AccountSync;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.ui.B2bStatusDialog;
import com.example.gajeontiming.ui.ForegroundTracker;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * FCM 푸시 받기.
 * 가격 알림: 서버가 매일 목표 가격 도달을 확인해 notification + data {type: "price_alert", alert_id, reason: "target"}을 보낸다.
 *   백그라운드 · 종료면 안드로이드가 알림을 띄우고, 앱을 쓰는 중이면 showPriceAlert가 같은 문구로 띄운다.
 * 사업자 승인 결과: 서버는 사업자 승인 · 거절 순간 notification(제목 · 내용) + data {type: "b2b_status", status: "active" | "rejected"}를 보낸다.
 *   앱이 꺼져 있거나 백그라운드: 안드로이드가 notification을 "회원 안내" 채널로 바로 띄운다 (이 메서드는 안 불림).
 *                              누르면 앱 첫 화면 → 홈이 열리고, 홈이 /api/auth/me로 상태를 맞춘다.
 *   앱을 쓰는 중(어느 화면이든): 여기로 온다 → 휴대폰 알림 + 지금 화면에 안내 창(모달),
 *                 회원 정보를 다시 받아 홈 · 마이페이지를 갱신한다.
 *   백그라운드 · 종료: 안드로이드가 알림만 띄운다 → 나중에 앱을 열면 홈이 상태 변화를 보고 안내 창을 띄운다.
 */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        PushTokens.log("새 토큰 받음");
        PushTokens.send(token);   // 로그인 상태일 때만 등록된다
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        PushTokens.log("푸시 받음 (앱 켜짐): " + data);
        if (!SessionManager.isLoggedIn()) return;
        if ("price_alert".equals(data.get("type"))) {
            showPriceAlert(message, data);
            return;
        }
        if (!"b2b_status".equals(data.get("type"))) return;

        boolean approved = "active".equals(data.get("status"));
        // 휴대폰 알림은 항상 보낸다
        AlertNotifier.notifyB2bResult(this, approved);
        ApprovalCheckWorker.sync(this);   // 결과가 나왔으니 주기적 확인은 멈춘다 (상태 갱신 뒤 다시 한 번 정리됨)
        if (ForegroundTracker.current() == null) {
            // 앱이 화면에 없음: 상태만 맞춰 둔다 → 다음에 앱을 열면 홈에서 안내 창 (SessionManager 안내 표시)
            AccountSync.refresh(null);
            return;
        }
        // 앱을 쓰는 중: 지금 화면에 바로 안내 창 + 회원 정보 갱신 · 다시 그리기
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity screen = ForegroundTracker.current();
            if (screen != null) B2bStatusDialog.show(screen, approved);
            AccountSync.refresh(() -> {
                if (screen != null) SessionManager.consumeStatusNotice();   // 방금 띄웠으니 나중에 또 띄우지 않게
                AccountSync.notifyChanged();
            });
        });
    }

    /**
     * 가격 알림 (서버가 매일 확인, data {type: "price_alert", alert_id, reason: "target"}).
     * 앱이 백그라운드면 안드로이드가 알림을 띄우고, 앱을 쓰는 중이면 여기서 같은 문구로 직접 띄운다.
     * 목표 가격 도달(target)만 보여 준다 (매입 신호 알림은 서버에서 껐다).
     */
    private void showPriceAlert(RemoteMessage message, Map<String, String> data) {
        String reason = data.get("reason");
        if (reason != null && !"target".equals(reason)) return;
        RemoteMessage.Notification n = message.getNotification();
        if (n == null || n.getTitle() == null || n.getBody() == null) return;
        long alertId;
        try {
            alertId = Long.parseLong(data.get("alert_id"));
        } catch (NumberFormatException e) {
            alertId = 0;
        }
        AlertNotifier.notifyPriceAlert(this, alertId, n.getTitle(), n.getBody(), data);
    }
}
