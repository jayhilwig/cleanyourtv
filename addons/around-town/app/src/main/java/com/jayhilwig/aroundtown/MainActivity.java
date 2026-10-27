package com.jayhilwig.aroundtown;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final long REFRESH_MS = 3L * 60 * 60 * 1000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView city, temperature, conditions, highLow, updated, status;
    private String zip;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(0xFF05080D);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(72), dp(48), dp(72), dp(40));
        setContentView(root);
        TextView heading = label("AROUND TOWN", 18, 0xFF9CAEC1);
        root.addView(heading);
        city = label("", 34, 0xFFFFFFFF); root.addView(city);
        temperature = label("—", 100, 0xFFFFFFFF); root.addView(temperature);
        conditions = label("", 31, 0xFFE2EAF2); root.addView(conditions);
        highLow = label("", 25, 0xFFB4C5D5); root.addView(highLow);
        updated = label("", 20, 0xFF8FA3B7); root.addView(updated);
        TextView change = label("Change ZIP code", 22, 0xFFFFFFFF);
        change.setPadding(dp(20), dp(12), dp(20), dp(12));
        change.setBackgroundColor(0xFF253B51);
        change.setFocusable(true);
        change.setOnClickListener(v -> askZip());
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-2, -2);
        button.topMargin = dp(40); root.addView(change, button);
        status = label("", 18, 0xFF9CAEC1);
        LinearLayout.LayoutParams note = new LinearLayout.LayoutParams(-1, -2);
        note.topMargin = dp(28); root.addView(status, note);
        zip = getPreferences(0).getString("zip", "98512");
        change.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        showCached();
        if (System.currentTimeMillis() - getPreferences(0).getLong("fetched", 0) >= REFRESH_MS) refresh();
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void askZip() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        field.setText(zip);
        new AlertDialog.Builder(this).setTitle("US ZIP code").setView(field)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = field.getText().toString().trim();
                    if (!value.matches("[0-9]{5}")) { status.setText("Enter a five-digit ZIP code."); return; }
                    zip = value;
                    getPreferences(0).edit().putString("zip", zip).remove("current").remove("forecast").putLong("fetched", 0).apply();
                    city.setText("ZIP " + zip); temperature.setText("—"); conditions.setText(""); highLow.setText(""); updated.setText("");
                    refresh();
                }).show();
    }

    private void showCached() {
        String current = getPreferences(0).getString("current", "");
        String forecast = getPreferences(0).getString("forecast", "");
        if (current.isEmpty()) { city.setText("ZIP " + zip); status.setText("Loading weather…"); return; }
        try { render(new JSONObject(current), forecast.isEmpty() ? null : new JSONObject(forecast)); }
        catch (Exception ignored) { status.setText("Weather unavailable. Try again later."); }
    }

    private void refresh() {
        final String requestedZip = zip;
        status.setText("Updating weather…");
        worker.execute(() -> {
            try {
                String base = "https://api.openweathermap.org/data/2.5/";
                String query = "?zip=" + requestedZip + ",US&units=imperial&appid=" + BuildConfig.OPENWEATHER_KEY;
                JSONObject now = new JSONObject(fetch(base + "weather" + query));
                JSONObject later = new JSONObject(fetch(base + "forecast" + query));
                if (!requestedZip.equals(zip)) return;
                getPreferences(0).edit().putString("current", now.toString()).putString("forecast", later.toString())
                        .putLong("fetched", System.currentTimeMillis()).apply();
                runOnUiThread(() -> { try { render(now, later); } catch (Exception ignored) { status.setText("Weather unavailable."); } });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Could not update weather. Showing the last saved result if available."));
            }
        });
    }

    private String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
        try {
            if (connection.getResponseCode() != 200) throw new Exception("Weather request failed");
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }

    private void render(JSONObject now, JSONObject forecast) throws Exception {
        city.setText(now.optString("name", "ZIP " + zip));
        JSONObject main = now.getJSONObject("main");
        temperature.setText(Math.round(main.getDouble("temp")) + "°F");
        JSONObject weather = now.getJSONArray("weather").getJSONObject(0);
        int code = weather.optInt("id");
        String symbol = code >= 200 && code < 600 ? "☔  " : code >= 600 && code < 700 ? "❄  " : code == 800 ? "☀  " : "☁  ";
        conditions.setText(symbol + weather.optString("description", ""));
        double high = main.getDouble("temp"), low = high;
        if (forecast != null) {
            long offset = forecast.optJSONObject("city") == null ? 0 : forecast.getJSONObject("city").optLong("timezone");
            long localDay = Math.floorDiv(now.optLong("dt") + offset, 86400);
            JSONArray entries = forecast.optJSONArray("list");
            if (entries != null) for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (Math.floorDiv(entry.optLong("dt") + offset, 86400) != localDay) continue;
                JSONObject data = entry.getJSONObject("main");
                high = Math.max(high, data.optDouble("temp_max", data.optDouble("temp", high)));
                low = Math.min(low, data.optDouble("temp_min", data.optDouble("temp", low)));
            }
        }
        highLow.setText("Today  H " + Math.round(high) + "°  ·  L " + Math.round(low) + "°");
        SimpleDateFormat clock = new SimpleDateFormat("h:mm a", Locale.US);
        clock.setTimeZone(TimeZone.getDefault());
        updated.setText("Updated " + clock.format(new Date(now.optLong("dt") * 1000)));
        status.setText("");
    }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
