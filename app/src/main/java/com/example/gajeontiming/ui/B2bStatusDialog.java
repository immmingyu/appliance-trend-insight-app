package com.example.gajeontiming.ui;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.example.gajeontiming.R;

/** 사업자 승인 · 거절 결과 안내 창 (앱을 쓰는 중에 푸시를 받았을 때) */
public final class B2bStatusDialog {

    private B2bStatusDialog() { }

    public static void show(Activity activity, boolean approved) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(approved ? R.string.b2b_approved_title : R.string.b2b_rejected_title)
                .setMessage(approved ? R.string.b2b_approved_message : R.string.b2b_rejected)
                .setPositiveButton(R.string.pending_ok, null)
                .show();
    }
}
