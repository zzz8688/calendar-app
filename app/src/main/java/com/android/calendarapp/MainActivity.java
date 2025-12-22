package com.android.calendarapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.calendarapp.database.EventDao;
import com.android.calendarapp.model.CalendarEvent;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.android.calendarapp.utils.ICalParser;
import com.android.calendarapp.utils.LunarUtils;
import androidx.core.content.FileProvider;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.TimeZone;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.AlarmManager;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private RecyclerView rvEvents;
    private View layoutWeekPicker, layoutMonthPicker;
    private RecyclerView rvWeeks, rvMonths;
    private TextView tvWeekMonthTitle, tvMonthYearTitle;
    private TabLayout tabLayout;
    private FloatingActionButton fabAdd;
    private TextView tvSelectedDate;
    private EventsAdapter adapter;
    private EventDao eventDao;
    private int currentViewMode = 0;
    private final Calendar selectedDate = Calendar.getInstance();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Calendar pickerDate = (Calendar) selectedDate.clone();
    private int selectedWeekIndex = -1;
    private int selectedMonthIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Locale.setDefault(Locale.CHINA);
        android.content.res.Configuration config = getResources().getConfiguration();
        config.setLocale(Locale.CHINA);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        checkPermissions();

        eventDao = new EventDao(this);
        initViews();
        setupRecyclerView();
        setupCalendarView();
        setupTabLayout();
        
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditEventActivity.class);
            intent.putExtra(AddEditEventActivity.EXTRA_SELECTED_DATE, selectedDate.getTimeInMillis());
            startActivity(intent);
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "calendar_reminders",
                    "日程提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.setDescription("用于显示日程提醒通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要闹钟权限")
                        .setMessage("为了能准时提醒您的日程，请在设置中允许应用设置闹钟和提醒。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import_url) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_import_local) {
            importFromLocalBackup();
            return true;
        } else if (id == R.id.action_export_all) {
            exportCalendar();
            return true;
        } else if (id == R.id.action_clear_all) {
            showClearAllConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showClearAllConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("清空确认")
                .setMessage("确定要清空所有日程吗？此操作不可撤销。")
                .setPositiveButton("确定清空", (dialog, which) -> {
                    eventDao.deleteAllEvents();
                    loadEvents();
                    Toast.makeText(this, "已清空所有日程", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showImportDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://example.com/calendar.ics");
        input.setText("https://cdn.jsdelivr.net/npm/chinese-days/dist/holidays.ics");
        
        new AlertDialog.Builder(this)
                .setTitle("从 URL 导入日程")
                .setView(input)
                .setPositiveButton("开始导入", (dialog, which) -> {
                    String url = input.getText().toString();
                    if (!url.isEmpty()) {
                        importCalendar(url);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportCalendar() {
        executorService.execute(() -> {
            List<CalendarEvent> allEvents = eventDao.getAllEvents();
            if (allEvents.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "没有可导出的日程", Toast.LENGTH_SHORT).show());
                return;
            }

            StringBuilder ical = new StringBuilder();
            ical.append("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//CalendarApp//Android//EN\n");
            SimpleDateFormat icalFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
            icalFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            for (CalendarEvent event : allEvents) {
                ical.append("BEGIN:VEVENT\n");
                ical.append("SUMMARY:").append(escape(event.getTitle())).append("\n");
                if (event.getDescription() != null) ical.append("DESCRIPTION:").append(escape(event.getDescription())).append("\n");
                if (event.getLocation() != null) ical.append("LOCATION:").append(escape(event.getLocation())).append("\n");
                ical.append("DTSTART:").append(icalFormat.format(new java.util.Date(event.getStartTime()))).append("\n");
                ical.append("DTEND:").append(icalFormat.format(new java.util.Date(event.getEndTime()))).append("\n");
                ical.append("X-ALL-DAY:").append(event.isAllDay() ? "1" : "0").append("\n");
                ical.append("X-REMINDER-MINUTES:").append(event.getReminderMinutes()).append("\n");
                ical.append("END:VEVENT\n");
            }
            ical.append("END:VCALENDAR");

            try {
                File backupPath = new File(getFilesDir(), "backups");
                if (!backupPath.exists()) backupPath.mkdirs();
                File file = new File(backupPath, "calendar_backup.ics");
                FileOutputStream stream = new FileOutputStream(file);
                stream.write(ical.toString().getBytes());
                stream.close();

                runOnUiThread(() -> Toast.makeText(this, "日程已备份至本地", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "备份失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    private void importFromLocalBackup() {
        executorService.execute(() -> {
            File file = new File(getFilesDir(), "backups/calendar_backup.ics");
            if (!file.exists()) {
                runOnUiThread(() -> Toast.makeText(this, "未找到本地备份文件", Toast.LENGTH_SHORT).show());
                return;
            }

            try (InputStream is = new FileInputStream(file)) {
                List<CalendarEvent> events = ICalParser.parse(is);
                for (CalendarEvent event : events) {
                    eventDao.insertEvent(event);
                }
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "已从本地备份恢复 " + events.size() + " 条日程", Toast.LENGTH_SHORT).show();
                    loadEvents();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "恢复失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void importCalendar(String urlString) {
        runOnUiThread(() -> Toast.makeText(this, "正在导入，请稍候...", Toast.LENGTH_SHORT).show());
        executorService.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestMethod("GET");
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        List<CalendarEvent> events = ICalParser.parse(is);
                        for (CalendarEvent event : events) {
                            eventDao.insertEvent(event);
                        }
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "成功导入 " + events.size() + " 条日程", Toast.LENGTH_SHORT).show();
                            loadEvents();
                        });
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接失败，响应码: " + responseCode, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "导入出错: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setupTabLayout() {
        tabLayout.getTabAt(currentViewMode).select();
        updateViewMode();
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentViewMode = tab.getPosition();
                updateViewMode();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateViewMode() {
        calendarView.setVisibility(android.view.View.GONE);
        layoutWeekPicker.setVisibility(android.view.View.GONE);
        layoutMonthPicker.setVisibility(android.view.View.GONE);

        switch (currentViewMode) {
            case 0:
                calendarView.setVisibility(android.view.View.VISIBLE);
                updateDateLabel();
                loadEvents();
                break;
            case 1:
                layoutWeekPicker.setVisibility(android.view.View.VISIBLE);
                pickerDate.setTimeInMillis(selectedDate.getTimeInMillis());
                selectedWeekIndex = -1;
                updateWeekPickerUI();
                loadEvents();
                break;
            case 2:
                layoutMonthPicker.setVisibility(android.view.View.VISIBLE);
                pickerDate.setTimeInMillis(selectedDate.getTimeInMillis());
                selectedMonthIndex = selectedDate.get(Calendar.MONTH);
                updateMonthPickerUI();
                loadEvents();
                break;
        }
    }

    private void initViews() {
        calendarView = findViewById(R.id.calendarView);
        rvEvents = findViewById(R.id.rv_events);
        tabLayout = findViewById(R.id.tabLayout);
        fabAdd = findViewById(R.id.fab_add);
        tvSelectedDate = findViewById(R.id.tv_selected_date);

        layoutWeekPicker = findViewById(R.id.layout_week_picker);
        layoutMonthPicker = findViewById(R.id.layout_month_picker);
        rvWeeks = findViewById(R.id.rv_weeks);
        rvMonths = findViewById(R.id.rv_months);
        tvWeekMonthTitle = findViewById(R.id.tv_week_month_title);
        tvMonthYearTitle = findViewById(R.id.tv_month_year_title);

        findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            pickerDate.add(Calendar.MONTH, -1);
            updateWeekPickerUI();
        });
        findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            pickerDate.add(Calendar.MONTH, 1);
            updateWeekPickerUI();
        });
        findViewById(R.id.btn_prev_year).setOnClickListener(v -> {
            pickerDate.add(Calendar.YEAR, -1);
            updateMonthPickerUI();
        });
        findViewById(R.id.btn_next_year).setOnClickListener(v -> {
            pickerDate.add(Calendar.YEAR, 1);
            updateMonthPickerUI();
        });

        rvWeeks.setLayoutManager(new LinearLayoutManager(this));
        rvMonths.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));

        updateDateLabel();
    }

    private void updateWeekPickerUI() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月", Locale.CHINA);
        tvWeekMonthTitle.setText(sdf.format(pickerDate.getTime()));
        
        List<String> weekLabels = new java.util.ArrayList<>();
        List<long[]> weekRanges = new java.util.ArrayList<>();
        
        Calendar cal = (Calendar) pickerDate.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int month = cal.get(Calendar.MONTH);
        
        SimpleDateFormat rangeSdf = new SimpleDateFormat("MM.dd", Locale.CHINA);
        int weekCount = 1;
        
        while (cal.get(Calendar.MONTH) == month) {
            Calendar weekStart = (Calendar) cal.clone();
            Calendar weekEnd = (Calendar) cal.clone();
            while (weekEnd.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && weekEnd.get(Calendar.DAY_OF_MONTH) < weekEnd.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                weekEnd.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            weekLabels.add("第" + weekCount + "周 (" + rangeSdf.format(weekStart.getTime()) + " - " + rangeSdf.format(weekEnd.getTime()) + ")");
            weekRanges.add(new long[]{getStartOfDay(weekStart.getTimeInMillis()), getEndOfDay(weekEnd.getTimeInMillis())});
            
            cal.setTimeInMillis(weekEnd.getTimeInMillis());
            
            if (selectedWeekIndex == -1 && selectedDate.getTimeInMillis() >= weekStart.getTimeInMillis() && selectedDate.getTimeInMillis() <= weekEnd.getTimeInMillis()) {
                selectedWeekIndex = weekCount - 1;
            }

            cal.add(Calendar.DAY_OF_MONTH, 1);
            weekCount++;
        }
        
        setupSelectionRecyclerView(rvWeeks, weekLabels, selectedWeekIndex, position -> {
            selectedWeekIndex = position;
            long[] range = weekRanges.get(position);
            selectedDate.setTimeInMillis(range[0]);
            updateDateLabel(range[0], range[1]);
            loadEventsInRange(range[0], range[1]);
            updateWeekPickerUI();
        });

        if (selectedWeekIndex != -1 && selectedWeekIndex < weekRanges.size()) {
            long[] range = weekRanges.get(selectedWeekIndex);
            updateDateLabel(range[0], range[1]);
        }
    }

    private void updateMonthPickerUI() {
        tvMonthYearTitle.setText(pickerDate.get(Calendar.YEAR) + "年");
        List<String> monthLabels = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) monthLabels.add(i + "月");
        
        setupSelectionRecyclerView(rvMonths, monthLabels, selectedMonthIndex, position -> {
            selectedMonthIndex = position;
            Calendar cal = (Calendar) pickerDate.clone();
            cal.set(Calendar.MONTH, position);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            selectedDate.setTimeInMillis(cal.getTimeInMillis());
            
            long start = getStartOfDay(cal.getTimeInMillis());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            long end = getEndOfDay(cal.getTimeInMillis());
            
            updateDateLabel(start, end);
            loadEventsInRange(start, end);
            updateMonthPickerUI();
        });

        if (selectedMonthIndex != -1) {
            Calendar cal = (Calendar) pickerDate.clone();
            cal.set(Calendar.MONTH, selectedMonthIndex);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            long start = getStartOfDay(cal.getTimeInMillis());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            long end = getEndOfDay(cal.getTimeInMillis());
            updateDateLabel(start, end);
        }
    }

    private void setupSelectionRecyclerView(RecyclerView rv, List<String> labels, int selectedIndex, OnItemClickListener listener) {
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @androidx.annotation.NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, -2));
                tv.setPadding(32, 24, 32, 24);
                tv.setTextSize(16);
                tv.setGravity(android.view.Gravity.CENTER);
                return new RecyclerView.ViewHolder(tv) {};
            }
            @Override
            public void onBindViewHolder(@androidx.annotation.NonNull RecyclerView.ViewHolder holder, int position) {
                TextView tv = (TextView) holder.itemView;
                tv.setText(labels.get(position));
                if (position == selectedIndex) {
                    tv.setBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"));
                    tv.setTextColor(android.graphics.Color.parseColor("#009688"));
                } else {
                    tv.setBackgroundResource(android.R.drawable.list_selector_background);
                    tv.setTextColor(android.graphics.Color.BLACK);
                }
                tv.setOnClickListener(v -> listener.onItemClick(position));
            }
            @Override
            public int getItemCount() { return labels.size(); }
        });
    }

    interface OnItemClickListener { void onItemClick(int position); }

    private void loadEventsInRange(long start, long end) {
        executorService.execute(() -> {
            List<CalendarEvent> events = eventDao.getEventsByRange(start, end);
            runOnUiThread(() -> {
                adapter.setShowDate(true);
                adapter.setEvents(events);
            });
        });
    }

    private void setupRecyclerView() {
        adapter = new EventsAdapter();
        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        rvEvents.setAdapter(adapter);
        adapter.setOnEventClickListener(event -> {
            Intent intent = new Intent(MainActivity.this, AddEditEventActivity.class);
            intent.putExtra(AddEditEventActivity.EXTRA_EVENT_ID, event.getId());
            intent.putExtra("EVENT", event);
            startActivity(intent);
        });
    }

    private void setupCalendarView() {
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate.set(year, month, dayOfMonth);
            updateDateLabel(getStartOfDay(selectedDate.getTimeInMillis()), getEndOfDay(selectedDate.getTimeInMillis()));
            loadEvents();
        });
    }

    private void updateDateLabel(long start, long end) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        if (currentViewMode == 0) {
            String lunar = LunarUtils.getLunarDate(selectedDate);
            tvSelectedDate.setText("已选日期: " + sdf.format(selectedDate.getTime()) + " (" + lunar + ")");
        } else if (currentViewMode == 1) {
            SimpleDateFormat weekSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            tvSelectedDate.setText("已选周: " + weekSdf.format(new java.util.Date(start)) + " - " + weekSdf.format(new java.util.Date(end)));
        } else {
            SimpleDateFormat monthSdf = new SimpleDateFormat("yyyy年MM月", Locale.CHINA);
            tvSelectedDate.setText("已选月份: " + monthSdf.format(new java.util.Date(start)));
        }
    }

    private void updateDateLabel() {
        updateDateLabel(getStartOfDay(selectedDate.getTimeInMillis()), getEndOfDay(selectedDate.getTimeInMillis()));
    }

    private void loadEvents() {
        long startRange, endRange;
        Calendar cal = (Calendar) selectedDate.clone();
        
        if (currentViewMode == 2) {
            adapter.setShowDate(true);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            startRange = getStartOfDay(cal.getTimeInMillis());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            endRange = getEndOfDay(cal.getTimeInMillis());
        } else if (currentViewMode == 1) {
            adapter.setShowDate(true);
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY && cal.get(Calendar.DAY_OF_MONTH) > 1) {
                cal.add(Calendar.DAY_OF_MONTH, -1);
            }
            startRange = getStartOfDay(cal.getTimeInMillis());
            
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && cal.get(Calendar.DAY_OF_MONTH) < cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            endRange = getEndOfDay(cal.getTimeInMillis());
        } else {
            adapter.setShowDate(false);
            startRange = getStartOfDay(selectedDate.getTimeInMillis());
            endRange = getEndOfDay(selectedDate.getTimeInMillis());
        }
        
        loadEventsInRange(startRange, endRange);
    }

    private long getStartOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long getEndOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }
}
