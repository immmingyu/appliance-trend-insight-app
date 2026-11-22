package com.example.gajeontiming;

import android.app.Application;

import com.example.gajeontiming.alert.AlertNotifier;
import com.example.gajeontiming.alert.PriceCheckWorker;
import com.example.gajeontiming.alert.PushTokens;
import com.example.gajeontiming.auth.KakaoLogin;
import com.example.gajeontiming.auth.SessionManager;
import com.example.gajeontiming.ui.ForegroundTracker;

/** 앱 전체에서 한 번만 준비할 것들. 매니페스트의 application android:name 으로 등록한다. */
public class GajeonApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SessionManager.init(this);
        AlertNotifier.createChannel(this);       // F-12 알림 채널
        PriceCheckWorker.scheduleDaily(this);    // F-12 하루 1회 가격 확인 예약
        KakaoLogin.init(this);                   // 카카오 로그인 SDK (키가 없으면 건너뜀)
        PushTokens.init(this);                   // FCM 푸시 (google-services.json이 없으면 꺼짐)
        registerActivityLifecycleCallbacks(new ForegroundTracker());   // 앱을 쓰는 중인지 (푸시 → 안내 창 / 알림)
    }
}
