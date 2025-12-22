package com.android.calendarapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.android.calendarapp.database.EventDao;
import com.android.calendarapp.model.CalendarEvent;
import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            rescheduleAllAlarms(context);
        }
    }

    private void rescheduleAllAlarms(Context context) {
        EventDao eventDao = new EventDao(context);
        List<CalendarEvent> events = eventDao.getAllEvents();
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (CalendarEvent event : events) {
            if (event.getEndTime() > System.currentTimeMillis()) {
                Intent alarmIntent = new Intent(context, ReminderReceiver.class);
                alarmIntent.putExtra("TITLE", event.getTitle());
                alarmIntent.putExtra("EVENT_ID", event.getId());

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context, 
                        (int) event.getId(), 
                        alarmIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                if (event.getReminderMinutes() == -1) continue;

                long triggerTime = event.getStartTime() - event.getReminderMinutes() * 60 * 1000;
                if (triggerTime < System.currentTimeMillis()) {
                    continue; 
                }

                if (alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    }
                }
            }
        }
    }
}
