package com.android.calendarapp;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.calendarapp.database.EventDao;
import com.android.calendarapp.model.CalendarEvent;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddEditEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "com.android.calendarapp.EXTRA_EVENT_ID";
    public static final String EXTRA_SELECTED_DATE = "com.android.calendarapp.EXTRA_SELECTED_DATE";

    private EditText etTitle, etLocation, etDescription;
    private CheckBox cbAllDay;
    private android.widget.Spinner spinnerReminder;
    private Button btnStartDate, btnStartTime, btnEndDate, btnEndTime, btnSave, btnDelete;
    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar endCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private EventDao eventDao;
    private CalendarEvent currentEvent;
    private final String[] reminderOptions = {"不提醒", "准时", "提前5分钟", "提前10分钟", "提前15分钟", "提前30分钟", "提前1小时"};
    private final int[] reminderValues = {-1, 0, 5, 10, 15, 30, 60};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_event);

        eventDao = new EventDao(this);
        initViews();
        setupListeners();

        long eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, -1);
        if (eventId != -1) {
            currentEvent = (CalendarEvent) getIntent().getSerializableExtra("EVENT");
            if (currentEvent != null) {
                populateUI(currentEvent);
                btnDelete.setVisibility(View.VISIBLE);
                setTitle("编辑日程");
            }
        } else {
            long selectedDate = getIntent().getLongExtra(EXTRA_SELECTED_DATE, System.currentTimeMillis());
            startCalendar.setTimeInMillis(selectedDate);
            endCalendar.setTimeInMillis(selectedDate + 3600000);
            updateDateButtons();
            setTitle("添加日程");
        }
    }

    private void initViews() {
        etTitle = findViewById(R.id.et_title);
        etLocation = findViewById(R.id.et_location);
        etDescription = findViewById(R.id.et_description);
        cbAllDay = findViewById(R.id.cb_all_day);
        spinnerReminder = findViewById(R.id.spinner_reminder);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reminderOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReminder.setAdapter(adapter);
        spinnerReminder.setSelection(2);
        btnStartDate = findViewById(R.id.btn_start_date);
        btnStartTime = findViewById(R.id.btn_start_time);
        btnEndDate = findViewById(R.id.btn_end_date);
        btnEndTime = findViewById(R.id.btn_end_time);
        btnSave = findViewById(R.id.btn_save);
        btnDelete = findViewById(R.id.btn_delete);
    }

    private void setupListeners() {
        btnStartDate.setOnClickListener(v -> showDatePicker(startCalendar, btnStartDate));
        btnStartTime.setOnClickListener(v -> showTimePicker(startCalendar, btnStartTime));
        btnEndDate.setOnClickListener(v -> showDatePicker(endCalendar, btnEndDate));
        btnEndTime.setOnClickListener(v -> showTimePicker(endCalendar, btnEndTime));
        cbAllDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnStartTime.setEnabled(!isChecked);
            btnEndTime.setEnabled(!isChecked);
        });
        btnSave.setOnClickListener(v -> saveEvent());
        btnDelete.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void showDeleteConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除日程")
                .setMessage("确定要删除这个日程吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteEvent())
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDatePicker(Calendar calendar, Button button) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            button.setText(dateFormat.format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar calendar, Button button) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            button.setText(timeFormat.format(calendar.getTime()));
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private void updateDateButtons() {
        btnStartDate.setText(dateFormat.format(startCalendar.getTime()));
        btnStartTime.setText(timeFormat.format(startCalendar.getTime()));
        btnEndDate.setText(dateFormat.format(endCalendar.getTime()));
        btnEndTime.setText(timeFormat.format(endCalendar.getTime()));
    }

    private void populateUI(CalendarEvent event) {
        etTitle.setText(event.getTitle());
        etLocation.setText(event.getLocation());
        etDescription.setText(event.getDescription());
        cbAllDay.setChecked(event.isAllDay());
        for (int i = 0; i < reminderValues.length; i++) {
            if (reminderValues[i] == event.getReminderMinutes()) {
                spinnerReminder.setSelection(i);
                break;
            }
        }
        startCalendar.setTimeInMillis(event.getStartTime());
        endCalendar.setTimeInMillis(event.getEndTime());
        updateDateButtons();
    }

    private void saveEvent() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("请输入标题");
            return;
        }

        if (currentEvent == null) {
            currentEvent = new CalendarEvent();
        }

        currentEvent.setTitle(title);
        currentEvent.setLocation(etLocation.getText().toString().trim());
        currentEvent.setDescription(etDescription.getText().toString().trim());
        currentEvent.setAllDay(cbAllDay.isChecked());
        currentEvent.setReminderMinutes(reminderValues[spinnerReminder.getSelectedItemPosition()]);
        currentEvent.setStartTime(startCalendar.getTimeInMillis());
        currentEvent.setEndTime(endCalendar.getTimeInMillis());

        if (currentEvent.getId() == 0) {
            long id = eventDao.insertEvent(currentEvent);
            currentEvent.setId(id);
            Toast.makeText(this, "日程已添加", Toast.LENGTH_SHORT).show();
        } else {
            eventDao.updateEvent(currentEvent);
            Toast.makeText(this, "日程已更新", Toast.LENGTH_SHORT).show();
        }

        scheduleNotification(currentEvent);
        finish();
    }

    private void scheduleNotification(CalendarEvent event) {
        if (event.getReminderMinutes() == -1) {
            cancelNotification(event.getId());
            return;
        }
        if (event.getEndTime() > System.currentTimeMillis()) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ReminderReceiver.class);
            intent.putExtra("TITLE", event.getTitle());
            intent.putExtra("EVENT_ID", event.getId());

            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) event.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            long triggerTime = event.getStartTime() - event.getReminderMinutes() * 60 * 1000;
            
            if (triggerTime < System.currentTimeMillis() && event.getStartTime() > System.currentTimeMillis()) {
                triggerTime = System.currentTimeMillis() + 5000;
            }

            if (triggerTime > System.currentTimeMillis() && alarmManager != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void deleteEvent() {
        if (currentEvent != null && currentEvent.getId() != 0) {
            cancelNotification(currentEvent.getId());
            eventDao.deleteEvent(currentEvent.getId());
        }
        finish();
    }

    private void cancelNotification(long eventId) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) eventId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}