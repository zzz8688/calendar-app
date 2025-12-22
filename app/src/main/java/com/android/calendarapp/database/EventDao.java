package com.android.calendarapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.android.calendarapp.model.CalendarEvent;
import java.util.ArrayList;
import java.util.List;

public class EventDao {
    private final DatabaseHelper dbHelper;

    public EventDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public long insertEvent(CalendarEvent event) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_TITLE, event.getTitle());
        values.put(DatabaseHelper.COLUMN_DESCRIPTION, event.getDescription());
        values.put(DatabaseHelper.COLUMN_START_TIME, event.getStartTime());
        values.put(DatabaseHelper.COLUMN_END_TIME, event.getEndTime());
        values.put(DatabaseHelper.COLUMN_LOCATION, event.getLocation());
        values.put(DatabaseHelper.COLUMN_IS_ALL_DAY, event.isAllDay() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_REMINDER_MINUTES, event.getReminderMinutes());

        long id = db.insert(DatabaseHelper.TABLE_EVENTS, null, values);
        db.close();
        return id;
    }

    public void updateEvent(CalendarEvent event) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_TITLE, event.getTitle());
        values.put(DatabaseHelper.COLUMN_DESCRIPTION, event.getDescription());
        values.put(DatabaseHelper.COLUMN_START_TIME, event.getStartTime());
        values.put(DatabaseHelper.COLUMN_END_TIME, event.getEndTime());
        values.put(DatabaseHelper.COLUMN_LOCATION, event.getLocation());
        values.put(DatabaseHelper.COLUMN_IS_ALL_DAY, event.isAllDay() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_REMINDER_MINUTES, event.getReminderMinutes());

        db.update(DatabaseHelper.TABLE_EVENTS, values,
                DatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(event.getId())});
        db.close();
    }

    public void deleteEvent(long eventId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_EVENTS,
                DatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(eventId)});
        db.close();
    }

    public void deleteAllEvents() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_EVENTS, null, null);
        db.close();
    }

    public List<CalendarEvent> getEventsByRange(long startMillis, long endMillis) {
        List<CalendarEvent> events = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = DatabaseHelper.COLUMN_START_TIME + " >= ? AND " +
                           DatabaseHelper.COLUMN_START_TIME + " <= ?";
        String[] selectionArgs = {String.valueOf(startMillis), String.valueOf(endMillis)};

        Cursor cursor = db.query(DatabaseHelper.TABLE_EVENTS, null, selection, selectionArgs, null, null, DatabaseHelper.COLUMN_START_TIME + " ASC");

        if (cursor.moveToFirst()) {
            do {
                events.add(cursorToEvent(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return events;
    }

    public List<CalendarEvent> getAllEvents() {
        List<CalendarEvent> events = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_EVENTS, null, null, null, null, null, DatabaseHelper.COLUMN_START_TIME + " ASC");

        if (cursor.moveToFirst()) {
            do {
                events.add(cursorToEvent(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return events;
    }

    private CalendarEvent cursorToEvent(Cursor cursor) {
        CalendarEvent event = new CalendarEvent();
        event.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)));
        event.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TITLE)));
        event.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DESCRIPTION)));
        event.setStartTime(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_START_TIME)));
        event.setEndTime(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_END_TIME)));
        event.setLocation(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOCATION)));
        event.setAllDay(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_ALL_DAY)) == 1);
        event.setReminderMinutes(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_REMINDER_MINUTES)));
        return event;
    }
}