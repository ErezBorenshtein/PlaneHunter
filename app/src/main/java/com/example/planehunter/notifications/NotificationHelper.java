package com.example.planehunter.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.example.planehunter.R;
import com.example.planehunter.ui.activities.MainActivity;

/**
 * Utility class to manage notification channels and build notifications for the application.
 */
public class NotificationHelper {

    /** Channel ID for the foreground service notification. */
    public static final String CHANNEL_ID_FOREGROUND = "PlaneHunterForeground";
    /** Channel ID for aircraft alert notifications. */
    public static final String CHANNEL_ID_ALERTS = "PlaneHunterAlerts";
    /** Intent extra key indicating the app was opened from a notification. */
    public static final String FROM_NOTIFICATION = "from_notification";
    /** Intent extra key for passing an aircraft's ICAO 24-bit address. */
    public static final String ICAO_24 = "icao24";

    /** Static instance of NotificationManager for sending and managing notifications. */
    private static NotificationManager notificationManager;

    /**
     * Ensures that the required notification channels are created on the device.
     * @param ctx The application context.
     */
    public static void ensureChannels(Context ctx) {

        notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        NotificationChannel forground = new NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "PlaneHunter Service",
                NotificationManager.IMPORTANCE_LOW
        );

        notificationManager.createNotificationChannel(forground);

        NotificationChannel alerts = new NotificationChannel(CHANNEL_ID_ALERTS,
                "Aircraft Alerts",
                NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(alerts);
    }

    /**
     * Builds a notification to be used with a foreground service.
     * @param ctx The application context.
     * @param title The title of the notification.
     * @param text The body text of the notification.
     * @return A built Notification object.
     */
    public static Notification buildForegroundNotification(Context ctx, String title, String text) {
        return new NotificationCompat.Builder(ctx, CHANNEL_ID_FOREGROUND)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.forground_icon)
                .setOngoing(true)
                .build();
    }

    /**
     * Displays a notification alert for a specific aircraft.
     * @param context The application context.
     * @param title The title of the alert.
     * @param text The body text of the alert.
     * @param icao24 The ICAO 24-bit address of the aircraft.
     */
    public static void showAircraftAlert(Context context, String title, String text, String icao24){
        Intent intent = new Intent( context , MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); //returns to main activity. if already open dont create another one

        intent.putExtra(FROM_NOTIFICATION, true);
        intent.putExtra(ICAO_24, icao24);

        PendingIntent pendingIntent =PendingIntent.getActivity(
                context,
                icao24 !=null ? icao24.hashCode(): (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new  NotificationCompat.Builder(context,CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.plane_placeholder)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        notificationManager.notify((int) System.currentTimeMillis(), notification);
    }
}