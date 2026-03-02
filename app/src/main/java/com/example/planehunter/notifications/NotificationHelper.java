package com.example.planehunter.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.planehunter.R;

public class NotificationHelper {

    public static final String CHANNEL_ID_FOREGROUND = "PlaneHunterForeground";

    public static void ensureChannels(Context ctx) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel fg = new NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "PlaneHunter Service",
                NotificationManager.IMPORTANCE_LOW
        );

        nm.createNotificationChannel(fg);
    }

    public static Notification buildForegroundNotification(Context ctx, String title, String text) {
        return new NotificationCompat.Builder(ctx, CHANNEL_ID_FOREGROUND)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }
}