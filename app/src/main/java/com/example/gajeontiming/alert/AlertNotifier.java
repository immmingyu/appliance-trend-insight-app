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
import com.example.gajeontiming.R;

import java.text.NumberFormat;
import java.util.Locale;

/** F-12 휴대폰 알림을 만든다. 누르면 가격 알림 목록 화면이 열린다. */
public final class AlertNotifier {

    private static final String CHANNEL_ID = "price_alert";

    private AlertNotifier() { }

    /** 안드로이드 8.0부터는 알림 채널을 먼저 만들어야 알림이 뜬다 (앱 시작 시 한 번) */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "가격 알림", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("등록한 제품이 목표 가격 아래로 내려가면 알려드려요");
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static void notifyPriceDrop(Context context, long alertId, String productName,
                                       long price, long target) {
        // 안드로이드 13부터는 사용자가 알림 권한을 허용해야 한다
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);
        Intent open = new Intent(context, AlertListActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, (int) alertId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("목표 가격 도달: " + won.format(price) + "원")
                .setContentText(productName)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        productName + "\n지금 " + won.format(price) + "원 (목표 " + won.format(target) + "원)"))
                .setContentIntent(pending)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify((int) alertId, builder.build());
    }
}
