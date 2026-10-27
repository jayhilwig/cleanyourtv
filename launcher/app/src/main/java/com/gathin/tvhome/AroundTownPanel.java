package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** One home panel. Around Town owns the ZIP, API key, network requests, and cache. */
final class AroundTownPanel {
    private static final Uri WEATHER = Uri.parse("content://com.jayhilwig.aroundtown.weather");
    private final Context context;
    private final LinearLayout panel;
    private final TextView place, temperature, conditions, highLow;
    private final TextView[] forecastDay = new TextView[2];
    private final TextView[] forecastIcon = new TextView[2];
    private final TextView[] forecastRange = new TextView[2];

    AroundTownPanel(Context context, FrameLayout root) {
        this.context = context;
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(17 * density);
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, pad);
        panel.setMinimumHeight(Math.round(172 * density));
        panel.setFocusable(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF2151921);
        background.setCornerRadius(16 * density);
        background.setStroke(Math.max(1, Math.round(density)), 0xFF343943);
        panel.setBackground(background);
        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout weather = new LinearLayout(context);
        weather.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout currentWeather = new LinearLayout(context);
        currentWeather.setOrientation(LinearLayout.VERTICAL);
        currentWeather.addView(text("TODAY", 12, 0xFFADB7C5));
        temperature = text("—", 40, Color.WHITE);
        conditions = text("Open Around Town to load weather", 17, 0xFFE1E7EE);
        highLow = text("", 14, 0xFFADB7C5);
        currentWeather.addView(temperature);
        currentWeather.addView(conditions);
        currentWeather.addView(highLow);
        weather.addView(currentWeather, new LinearLayout.LayoutParams(0, -2, 1.35f));
        for (int i = 0; i < 2; i++) {
            LinearLayout day = new LinearLayout(context);
            day.setOrientation(LinearLayout.VERTICAL);
            forecastDay[i] = text("", 12, 0xFFADB7C5);
            forecastIcon[i] = text("", 24, Color.WHITE);
            forecastRange[i] = text("", 14, 0xFFE1E7EE);
            day.addView(forecastDay[i]);
            day.addView(forecastIcon[i]);
            day.addView(forecastRange[i]);
            weather.addView(day, new LinearLayout.LayoutParams(0, -2, 1));
        }
        columns.addView(weather, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout events = new LinearLayout(context);
        events.setOrientation(LinearLayout.VERTICAL);
        LinearLayout eventsHeader = new LinearLayout(context);
        eventsHeader.setOrientation(LinearLayout.HORIZONTAL);
        eventsHeader.addView(text("LOCAL EVENTS", 12, 0xFFADB7C5), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("AROUND TOWN", 12, 0xFFADB7C5);
        title.setGravity(Gravity.RIGHT);
        heading.addView(title);
        place = text("", 12, 0xFFADB7C5);
        place.setGravity(Gravity.RIGHT);
        heading.addView(place);
        eventsHeader.addView(heading);
        events.addView(eventsHeader);
        TextView empty = text("Local events coming soon.", 19, 0xFFE1E7EE);
        empty.setPadding(0, Math.round(12 * density), 0, 0);
        events.addView(empty);
        columns.addView(events, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(columns);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        position.leftMargin = position.rightMargin = Math.round(56 * density);
        position.topMargin = Math.round(205 * density);
        root.addView(panel, 4, position);
        refresh();
    }

    void syncScroll(int offset) {
        panel.setTranslationY(-Math.max(0, offset));
    }

    void refresh() {
        try {
            Bundle data = context.getContentResolver().call(WEATHER, "snapshot", null, null);
            if (data == null) return;
            String zip = data.getString("zip", "");
            String raw = data.getString("current", "");
            if (raw.isEmpty()) { place.setText(zip); return; }
            JSONObject now = new JSONObject(raw);
            place.setText(now.optString("name", "") + (zip.isEmpty() ? "" : " · " + zip));
            JSONObject main = now.getJSONObject("main");
            double current = main.getDouble("temp");
            temperature.setText(Math.round(current) + "°F");
            JSONObject state = now.getJSONArray("weather").getJSONObject(0);
            int code = state.optInt("id");
            String icon = iconFor(code);
            conditions.setText(icon + "  " + state.optString("description", "Weather"));
            double high = current, low = current;
            double[] futureHigh = {Double.NaN, Double.NaN};
            double[] futureLow = {Double.NaN, Double.NaN};
            int[] futureCode = {0, 0};
            int[] closestNoon = {24, 24};
            String rawForecast = data.getString("forecast", "");
            long offset = 0;
            long day = Math.floorDiv(now.optLong("dt"), 86400);
            if (!rawForecast.isEmpty()) {
                JSONObject forecast = new JSONObject(rawForecast);
                JSONObject city = forecast.optJSONObject("city");
                offset = city == null ? 0 : city.optLong("timezone");
                day = Math.floorDiv(now.optLong("dt") + offset, 86400);
                JSONArray list = forecast.optJSONArray("list");
                if (list != null) for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    long localTime = item.optLong("dt") + offset;
                    long daysAhead = Math.floorDiv(localTime, 86400) - day;
                    if (daysAhead < 0 || daysAhead > 2) continue;
                    JSONObject sample = item.getJSONObject("main");
                    if (daysAhead == 0) {
                        high = Math.max(high, sample.optDouble("temp_max", current));
                        low = Math.min(low, sample.optDouble("temp_min", current));
                    } else {
                        int index = (int) daysAhead - 1;
                        double sampleHigh = sample.optDouble("temp_max", current);
                        double sampleLow = sample.optDouble("temp_min", current);
                        futureHigh[index] = Double.isNaN(futureHigh[index]) ? sampleHigh : Math.max(futureHigh[index], sampleHigh);
                        futureLow[index] = Double.isNaN(futureLow[index]) ? sampleLow : Math.min(futureLow[index], sampleLow);
                        int hoursFromNoon = (int) Math.abs(Math.floorMod(localTime, 86400) / 3600 - 12);
                        if (hoursFromNoon < closestNoon[index]) {
                            closestNoon[index] = hoursFromNoon;
                            JSONArray weather = item.optJSONArray("weather");
                            if (weather != null && weather.length() > 0) futureCode[index] = weather.getJSONObject(0).optInt("id");
                        }
                    }
                }
            }
            highLow.setText("H: " + Math.round(high) + "°  L: " + Math.round(low) + "°");
            for (int i = 0; i < 2; i++) {
                forecastDay[i].setText(LocalDate.ofEpochDay(day + i + 1).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US));
                forecastIcon[i].setText(Double.isNaN(futureHigh[i]) ? "—" : iconFor(futureCode[i]));
                forecastRange[i].setText(Double.isNaN(futureHigh[i]) ? "Forecast unavailable" :
                        "H: " + Math.round(futureHigh[i]) + "°  L: " + Math.round(futureLow[i]) + "°");
            }
        } catch (Exception ignored) {
            place.setText("");
            temperature.setText("—");
            conditions.setText("Weather unavailable");
            highLow.setText("");
            for (int i = 0; i < 2; i++) {
                forecastDay[i].setText("");
                forecastIcon[i].setText("");
                forecastRange[i].setText("");
            }
        }
    }

    private static String iconFor(int code) {
        return code >= 200 && code < 600 ? "☔" : code >= 600 && code < 700 ? "❄" : code == 800 ? "☀" : "☁";
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setSingleLine(true);
        return view;
    }
}
