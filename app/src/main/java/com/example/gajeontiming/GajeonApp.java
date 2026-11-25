package com.example.gajeontiming;

import android.app.Application;

import androidx.work.WorkManager;

import com.example.gajeontiming.alert.AlertNotifier;
import com.example.gajeontiming.alert.PushTokens;
import com.example.gajeontiming.auth.KakaoLogin;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.network.ApiClient;
import com.example.gajeontiming.ui.ForegroundTracker;

/** 앱 전체에서 한 번만 준비할 것들. 매니페스트의 application android:name 으로 등록한다. */
public class GajeonApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SessionManager.init(this);
        ApiClient.init(this);                    // F-19 오프라인 캐시 폴더 준비
        AlertNotifier.createChannel(this);       // F-12 알림 채널
        // F-12 가격 확인은 서버가 매일 하고 FCM으로 알린다. 예전 버전이 예약해 둔 앱 쪽 확인 작업과
        // "마지막으로 알린 가격" 기록은 지운다 (남아 있으면 같은 알림이 두 번 갈 수 있다)
        WorkManager.getInstance(this).cancelUniqueWork("daily_price_check");
        deleteSharedPreferences("alert_notified");
        KakaoLogin.init(this);                   // 카카오 로그인 SDK (키가 없으면 건너뜀)
        PushTokens.init(this);                   // FCM 푸시 (google-services.json이 없으면 꺼짐)
        registerActivityLifecycleCallbacks(new ForegroundTracker());   // 앱을 쓰는 중인지 (푸시 → 안내 창 / 알림)
    }
}
