# Calendar App Product Report

## 1. Product Introduction
A native Android calendar application designed with a focus on backend logic and performance.
Key features include:
- **Calendar Views**: Month view with event list integration.
- **Event Management**: Create, edit, delete events with location and description.
- **Reminders**: Automated notifications 10 minutes before events.
- **Network Subscription**: Support for importing iCal (.ics) calendars via URL.

## 2. Program Design
- **Architecture**: MVC (Model-View-Controller) pattern adapted for Android.
  - **Model**: `CalendarEvent` class representing data.
  - **View**: XML layouts (`activity_main.xml`, `item_event.xml`).
  - **Controller**: Activities (`MainActivity`, `AddEditEventActivity`) handling logic.
- **Database**: SQLite with `SQLiteOpenHelper` and DAO pattern for clean data access.
- **Networking**: Raw `HttpURLConnection` for efficient, dependency-free network operations.

## 3. Technical Highlights (Backend Focus)

### A. Hand-written RFC 5545 Parser
Instead of using heavy external libraries, a custom `ICalParser` was implemented using Java IO and Regex.
- **Efficient Stream Processing**: Uses `BufferedReader` to process the file line-by-line, minimizing memory usage.
- **Line Unfolding**: Implements the RFC 5545 unfolding standard (merging multi-line properties).
- **Regex Parsing**: Uses `Pattern` and `Matcher` for robust property extraction.

### B. Database Design & Optimization
- **Schema Design**: Normalized `events` table with proper indexing on `start_time` for efficient range queries.
- **DAO Pattern**: Decoupled database logic from UI, making the code testable and maintainable.
- **Range Queries**: Optimized SQL queries to fetch only relevant events for the selected day/month.

### C. Network & Concurrency
- **ExecutorService**: Replaced legacy `AsyncTask` with `ExecutorService` for background network operations.
- **Raw HTTP**: Implemented network stack using `HttpURLConnection` to demonstrate understanding of HTTP protocols without relying on Retrofit/OkHttp (though easy to upgrade).

## 4. Software Architecture
[MainActivity] <-> [EventDao] <-> [SQLite Database]
       ^
       | (Network Import)
       v
[ICalParser] <-> [HttpURLConnection]
