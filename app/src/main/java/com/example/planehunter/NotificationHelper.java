package com.example.planehunter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    public static final String CHANNEL_ID_FOREGROUND = "PlaneHunterForeground";
    public static final String CHANNEL_ID_ALERTS = "PlaneHunterAlerts";

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel fg = new NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "PlaneHunter Service",
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ID_ALERTS,
                "PlaneHunter Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        nm.createNotificationChannel(fg);
        nm.createNotificationChannel(alerts);
    }

    public static Notification buildForegroundNotification(Context ctx) {
        return new NotificationCompat.Builder(ctx, CHANNEL_ID_FOREGROUND)
                .setContentTitle("PlaneHunter running")
                .setContentText("Checking planes near you every minute")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    public static void notifyPlaneFound(Context ctx, String title, String text, int notificationId) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build();

        nm.notify(notificationId, n);
    }
}
