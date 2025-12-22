package com.android.calendarapp.model;

import java.io.Serializable;

public class CalendarEvent implements Serializable {
    private long id;
    private String title;
    private String description;
    private long startTime;
    private long endTime;
    private String location;
    private boolean isAllDay;
    private int reminderMinutes = 5;

    public CalendarEvent() {}

    public CalendarEvent(long id, String title, String description, long startTime, long endTime, String location, boolean isAllDay, int reminderMinutes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.isAllDay = isAllDay;
        this.reminderMinutes = reminderMinutes;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isAllDay() { return isAllDay; }
    public void setAllDay(boolean allDay) { isAllDay = allDay; }
    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }
}