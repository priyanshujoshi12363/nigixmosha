package com.nigixmosha.app.studio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.nigixmosha.app.R;
import com.nigixmosha.app.ui.main.MainActivity;

public class WorkService extends Service {
    private static final String CHANNEL = "studio_work";
    private static final int ID = 42;
    private static boolean running;
    private static String lastText;

    public static void sync(Context context, StudioJobs jobs) {
        boolean busy = jobs.isBusy();
        if (busy) {
            String text = describe(context, jobs);
            if (running && text.equals(lastText)) return;
            lastText = text;
            Intent intent = new Intent(context, WorkService.class);
            try {
                ContextCompat.startForegroundService(context, intent);
                running = true;
            } catch (Exception ignored) {
            }
        } else if (running) {
            running = false;
            lastText = null;
            context.stopService(new Intent(context, WorkService.class));
        }
    }

    private static String describe(Context context, StudioJobs jobs) {
        if (jobs.produceStatus == StudioJobs.Status.RUNNING) {
            int pct = jobs.produceTotal == 0 ? 0 : Math.round(100f * jobs.produceDone / jobs.produceTotal);
            return context.getString(R.string.work_recording, pct, jobs.produceDone, jobs.produceTotal);
        }
        return jobs.directLabel != null ? jobs.directLabel : context.getString(R.string.work_directing);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.work_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_STUDIO)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        StudioJobs jobs = StudioJobs.get(this);
        boolean recording = jobs.produceStatus == StudioJobs.Status.RUNNING;
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_studio)
                .setContentTitle(getString(recording ? R.string.work_title_recording : R.string.work_title_directing))
                .setContentText(lastText != null ? lastText : describe(this, jobs))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setColor(ContextCompat.getColor(this, R.color.accent));
        if (recording) b.setProgress(Math.max(1, jobs.produceTotal), jobs.produceDone, false);
        else b.setProgress(0, 0, true);
        Notification n = b.build();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        try {
            ServiceCompat.startForeground(this, ID, n, type);
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
