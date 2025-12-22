package com.android.calendarapp.utils;

import com.android.calendarapp.model.CalendarEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ICalParser {

    private static final String BEGIN_VEVENT = "BEGIN:VEVENT";
    private static final String END_VEVENT = "END:VEVENT";

    public static List<CalendarEvent> parse(InputStream inputStream) throws IOException {
        List<CalendarEvent> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            List<String> unfoldedLines = new ArrayList<>();
            String currentLine = null;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    if (currentLine != null) {
                        currentLine += line.substring(1);
                    }
                } else {
                    if (currentLine != null) {
                        unfoldedLines.add(currentLine);
                    }
                    currentLine = line;
                }
            }
            if (currentLine != null) {
                unfoldedLines.add(currentLine);
            }

            CalendarEvent currentEvent = null;
            for (String unfolded : unfoldedLines) {
                if (unfolded.equalsIgnoreCase(BEGIN_VEVENT)) {
                    currentEvent = new CalendarEvent();
                } else if (unfolded.equalsIgnoreCase(END_VEVENT)) {
                    if (currentEvent != null) {
                        events.add(currentEvent);
                        currentEvent = null;
                    }
                } else if (currentEvent != null) {
                    parseProperty(unfolded, currentEvent);
                }
            }
        }
        return events;
    }

    private static void parseProperty(String line, CalendarEvent event) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) return;

        String keyPart = line.substring(0, colonIndex).trim().toUpperCase();
        String value = line.substring(colonIndex + 1);
        String propName = keyPart.split(";")[0];

        switch (propName) {
            case "SUMMARY":
                event.setTitle(unescape(value));
                break;
            case "DESCRIPTION":
                event.setDescription(unescape(value));
                break;
            case "LOCATION":
                event.setLocation(unescape(value));
                break;
            case "DTSTART":
                event.setStartTime(parseDate(value));
                break;
            case "DTEND":
                event.setEndTime(parseDate(value));
                break;
            case "X-ALL-DAY":
                event.setAllDay("1".equals(value.trim()));
                break;
            case "X-REMINDER-MINUTES":
                try {
                    event.setReminderMinutes(Integer.parseInt(value.trim()));
                } catch (Exception e) {}
                break;
        }
    }

    private static long parseDate(String dateStr) {
        try {
            if (dateStr.length() == 8) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(dateStr);
                return date != null ? date.getTime() : 0;
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
                if (dateStr.endsWith("Z")) {
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    dateStr = dateStr.substring(0, dateStr.length() - 1);
                }
                Date date = sdf.parse(dateStr);
                return date != null ? date.getTime() : 0;
            }
        } catch (ParseException e) {
            return 0;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n")
                    .replace("\\;", ";")
                    .replace("\\,", ",")
                    .replace("\\\\", "\\");
    }
}