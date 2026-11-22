package com.example.gajeontiming.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gajeontiming.auth.AccountSync;

import java.lang.ref.WeakReference;

/**
 * 지금 화면에 떠 있는(onResume ~ onPause) Activity를 기억한다.
 * 푸시를 받았을 때 앱을 쓰는 중인지 판단하고, 화면이 다시 보일 때 사업자 승인 결과를 안내하는 데 쓴다.
 * Activity를 직접 붙잡으면 메모리가 새므로 WeakReference로 둔다. GajeonApp에서 등록.
 */
public final class ForegroundTracker implements Application.ActivityLifecycleCallbacks {

    private static WeakReference<Activity> current = new WeakReference<>(null);

    /** 앱을 쓰는 중이면 그 화면, 백그라운드 · 종료 상태면 null */
    @Nullable
    public static Activity current() {
        Activity a = current.get();
        return (a == null || a.isFinishing() || a.isDestroyed()) ? null : a;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        current = new WeakReference<>(activity);
        AccountSync.onScreenResumed();   // 백그라운드에 있는 동안 나온 사업자 승인 · 거절 결과 안내
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (current.get() == activity) current = new WeakReference<>(null);
    }

    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
    @Override public void onActivityStarted(@NonNull Activity a) { }
    @Override public void onActivityStopped(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { }
}
