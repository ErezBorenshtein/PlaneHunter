package com.example.planehunter.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.example.planehunter.R;

public class NotificationHelper {

    public static final String CHANNEL_ID_FOREGROUND = "PlaneHunterForeground";
    public static final String CHANNEL_ID_ALERTS = "PlaneHunterAlerts";

    private static NotificationManager notificationManager;

    public static void ensureChannels(Context ctx) {

         notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        NotificationChannel fg = new NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "PlaneHunter Service",
                NotificationManager.IMPORTANCE_LOW
        );

        notificationManager.createNotificationChannel(fg);

        NotificationChannel alerts = new NotificationChannel(CHANNEL_ID_ALERTS,
                "Aircraft Alerts",
                NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(alerts);
    }

    public static Notification buildForegroundNotification(Context ctx, String title, String text) {
        return new NotificationCompat.Builder(ctx, CHANNEL_ID_FOREGROUND)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    public static void showAircraftAlert(Context context,
                             String title, String text,
                             String icao24){
        Intent intent = new Intent( context ,com.example.planehunter.ui.activities.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        intent.putExtra("from_notification", true);
        intent.putExtra("icao24", icao24);

        PendingIntent pendingIntent =PendingIntent.getActivity(
                context,
                icao24 !=null ? icao24.hashCode(): (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new  NotificationCompat.Builder(context,CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.air_plan)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        notificationManager.notify((int) System.currentTimeMillis(), notification);
    }
}