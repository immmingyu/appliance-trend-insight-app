package com.example.gajeontiming.alert;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.gajeontiming.AlertListActivity;
import com.example.gajeontiming.MainActivity;
import com.example.gajeontiming.R;

/** 휴대폰 알림: F-12 가격 알림(누르면 가격 알림 목록) · 사업자 승인 결과(누르면 홈) */
public final class AlertNotifier {

    private static final String CHANNEL_ID = "price_alert";
    private static final String ACCOUNT_CHANNEL_ID = "account";   // 회원 안내 (사업자 승인 결과)
    private static final int B2B_RESULT_ID = 900_001;             // 가격 알림 id(alertId)와 겹치지 않게

    private AlertNotifier() { }

    /** 안드로이드 8.0부터는 알림 채널을 먼저 만들어야 알림이 뜬다 (앱 시작 시 한 번) */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "가격 알림", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("등록한 제품이 목표 가격 아래로 내려가면 알려드려요");
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
        NotificationChannel account = new NotificationChannel(
                ACCOUNT_CHANNEL_ID, "회원 안내", NotificationManager.IMPORTANCE_DEFAULT);
        account.setDescription("사업자 회원 승인 결과를 알려드려요");
        nm.createNotificationChannel(account);
    }

    /** 사업자 승인 결과 알림. 누르면 앱 홈이 열린다 */
    public static void notifyB2bResult(Context context, boolean approved) {
        // 안드로이드 13부터는 사용자가 알림 권한을 허용해야 한다 (lint가 알아보도록 직접 확인)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            PushTokens.log("알림 권한이 없어 승인 결과 알림을 띄우지 못함");
            return;
        }
        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, B2B_RESULT_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = context.getString(approved ? R.string.b2b_approved_title : R.string.b2b_rejected_title);
        String text = context.getString(approved ? R.string.b2b_approved_message : R.string.b2b_rejected);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ACCOUNT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(B2B_RESULT_ID, builder.build());
    }


    /**
     * 서버 가격 알림 푸시(목표 가격 도달)를 앱을 쓰는 중에 받았을 때 직접 띄운다.
     * 제목 · 내용은 서버 문구 그대로 ("목표 가격 도달" / "제품명 지금 ○원 (목표 ○원)"). 누르면 가격 알림 목록
     */
    public static void notifyPriceAlert(Context context, long alertId, String title, String text) {
        // 안드로이드 13부터는 사용자가 알림 권한을 허용해야 한다
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent open = new Intent(context, AlertListActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, (int) alertId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify((int) alertId, builder.build());
    }
}
