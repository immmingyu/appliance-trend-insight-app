package com.example.gajeontiming.alert;

import androidx.annotation.NonNull;

import com.example.gajeontiming.auth.AccountSync;
import com.example.gajeontiming.auth.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * FCM 푸시 받기.
 * 서버는 사업자 승인 · 거절 순간 notification(제목 · 내용) + data {type: "b2b_status", status: "active" | "rejected"}를 보낸다.
 *   앱이 꺼져 있거나 백그라운드: 안드로이드가 notification을 "회원 안내" 채널로 바로 띄운다 (이 메서드는 안 불림).
 *                              누르면 앱 첫 화면 → 홈이 열리고, 홈이 /api/auth/me로 상태를 맞춘다.
 *   앱이 켜져 있음: 여기로 온다 → 회원 정보를 다시 받아 화면을 갱신하고 알림도 직접 띄운다.
 */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        PushTokens.send(token);   // 로그인 상태일 때만 등록된다
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        if (!"b2b_status".equals(data.get("type")) || !SessionManager.isLoggedIn()) return;

        boolean approved = "active".equals(data.get("status"));
        AlertNotifier.notifyB2bResult(this, approved);
        // 화면 갱신: 저장된 상태를 서버와 맞추고, 홈 화면이 열려 있으면 바로 다시 그린다.
        // 휴대폰 알림으로 이미 알렸으니 앱 안 승인 안내 창은 띄우지 않는다
        AccountSync.refresh(() -> {
            SessionManager.consumeApprovedNotice();
            AccountSync.notifyChanged();
        });
        ApprovalCheckWorker.sync(this);   // 결과가 나왔으니 주기적 확인은 멈춘다
    }
}
