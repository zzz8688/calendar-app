package com.android.calendarapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.android.calendarapp.model.CalendarEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventsAdapter extends RecyclerView.Adapter<EventsAdapter.EventViewHolder> {

    private List<CalendarEvent> events = new ArrayList<>();
    private OnEventClickListener listener;
    private boolean showDate = false;

    public interface OnEventClickListener {
        void onEventClick(CalendarEvent event);
    }

    public void setOnEventClickListener(OnEventClickListener listener) {
        this.listener = listener;
    }

    public void setShowDate(boolean showDate) {
        this.showDate = showDate;
    }

    public void setEvents(List<CalendarEvent> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(events.get(position));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvTime;
        private final TextView tvLocation;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvTime = itemView.findViewById(R.id.tv_event_time);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onEventClick(events.get(position));
                }
            });
        }

        public void bind(CalendarEvent event) {
            String title = event.getTitle();
            if (showDate) {
                SimpleDateFormat dateSdf = new SimpleDateFormat("MM-dd ", Locale.getDefault());
                title = dateSdf.format(new Date(event.getStartTime())) + title;
            }
            tvTitle.setText(title);

            if (event.isAllDay()) {
                tvTime.setText("全天");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String start = sdf.format(new Date(event.getStartTime()));
                String end = sdf.format(new Date(event.getEndTime()));
                tvTime.setText(String.format("%s - %s", start, end));
            }
            tvLocation.setText(event.getLocation() != null ? event.getLocation() : "");
        }
    }
}
