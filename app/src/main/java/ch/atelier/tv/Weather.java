package ch.atelier.tv;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small, bounded HTTPS requests, off the UI thread; no location permission. */
final class Weather implements AutoCloseable {
    interface Listener { void update(Report report); }
    static final long REFRESH_MS = 30 * 60 * 1000L, MAX_AGE_MS = 6 * 60 * 60 * 1000L;
    static final class City {
        final String name, detail;
        final double latitude, longitude;
        City(JSONObject value) throws Exception {
            name = ArtStore.clean(value.getString("name"), "", 60);
            latitude = value.getDouble("latitude"); longitude = value.getDouble("longitude");
            if (name.isEmpty() || !Double.isFinite(latitude) || !Double.isFinite(longitude) ||
                Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new IllegalArgumentException("Invalid city");
            detail = name + " · " + value.optString("admin1", "") + " · " + value.optString("country", "");
        }
    }
    static final class Day {
        final int code;
        final long high, low;
        Day(JSONObject daily, int index) throws Exception {
            code = daily.getJSONArray("weather_code").getInt(index);
            high = degrees(daily.getJSONArray("temperature_2m_max").getDouble(index));
            low = degrees(daily.getJSONArray("temperature_2m_min").getDouble(index));
        }
        String range() { return high + "° / " + low + "°"; }
    }
    static final class Report {
        final JSONObject json;
        final String city, time;
        final long temperature, observed;
        final int code;
        final boolean daylight;
        final Day tomorrow, afterTomorrow;
        Report(JSONObject value) throws Exception {
            json = value; city = value.getString("atelier_city");
            JSONObject current = value.getJSONObject("current");
            temperature = degrees(current.getDouble("temperature_2m")); code = current.getInt("weather_code");
            daylight = current.getInt("is_day") == 1; time = current.getString("time");
            observed = LocalDateTime.parse(time).toEpochSecond(ZoneOffset.ofTotalSeconds(value.getInt("utc_offset_seconds"))) * 1000;
            long age = System.currentTimeMillis() - observed;
            if (age < -60 * 60 * 1000L || age > MAX_AGE_MS) throw new IllegalArgumentException("Expired weather");
            JSONObject daily = value.getJSONObject("daily");
            LocalDate today = LocalDate.now(ZoneId.of(value.getString("timezone")));
            tomorrow = day(daily, today.plusDays(1)); afterTomorrow = day(daily, today.plusDays(2));
        }
        private static Day day(JSONObject daily, LocalDate date) throws Exception {
            JSONArray dates = daily.getJSONArray("time");
            for (int i = 0; i < dates.length(); i++) if (date.toString().equals(dates.getString(i))) return new Day(daily, i);
            return null;
        }
        boolean stale() { return System.currentTimeMillis() - observed > 90 * 60 * 1000L; }
    }

    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean visible, requesting, closed;
    private long lastAttempt;
    private int generation;
    private String location;
    private Report report;
    private final Runnable tick = () -> { if (visible && !closed) { refresh(false); schedule(); } };

    Weather(SharedPreferences prefs, Listener listener) {
        this.prefs = prefs; this.listener = listener; location = locationKey();
        try {
            JSONObject cached = new JSONObject(prefs.getString("weather_cache", ""));
            if (location.equals(cached.optString("atelier_location"))) report = new Report(cached);
        } catch (Exception ignored) { }
        listener.update(report);
    }
    String city() { return prefs.getString("weather_city", "Moudon"); }
    private String locationKey() { return prefs.getString("weather_lat", "46.66758") + "," + prefs.getString("weather_lon", "6.79781"); }
    void setVisible(boolean value) {
        boolean changed = visible != value; visible = value;
        handler.removeCallbacks(tick);
        if (!value || closed) return;
        if (!location.equals(locationKey())) { location = locationKey(); generation++; lastAttempt = 0; report = null; listener.update(null); }
        // Reparse cached dates on resume so yesterday's forecast is never labelled tomorrow.
        if (report != null) try { report = new Report(report.json); } catch (Exception error) { report = null; }
        if (changed) listener.update(report);
        refresh(false); schedule();
    }
    private void schedule() { handler.removeCallbacks(tick); if (visible && !closed) handler.postDelayed(tick, REFRESH_MS); }
    void refresh(boolean force) {
        if (closed || requesting || (!force && System.currentTimeMillis() - lastAttempt < (report == null ? 3 * 60 * 1000L : REFRESH_MS))) return;
        requesting = true; lastAttempt = System.currentTimeMillis();
        final int request = generation; final String selected = locationKey(), city = city();
        worker.execute(() -> {
            Report result = null;
            try {
                String[] coordinates = selected.split(",");
                JSONObject value = get("https://api.open-meteo.com/v1/forecast?latitude=" + URLEncoder.encode(coordinates[0], "UTF-8") +
                    "&longitude=" + URLEncoder.encode(coordinates[1], "UTF-8") +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=3&timezone=auto");
                value.put("atelier_city", city).put("atelier_location", selected); result = new Report(value);
            } catch (Exception ignored) { }
            final Report received = result;
            handler.post(() -> {
                requesting = false; if (closed) return;
                if (request != generation || !selected.equals(locationKey())) { if (visible) refresh(true); return; }
                if (received != null) { report = received; prefs.edit().putString("weather_cache", received.json.toString()).apply(); }
                else if (report != null) try { report = new Report(report.json); } catch (Exception error) { report = null; }
                listener.update(report);
            });
        });
    }
    void select(City city) {
        generation++; location = city.latitude + "," + city.longitude; report = null; lastAttempt = 0;
        prefs.edit().putString("weather_city", city.name).putString("weather_lat", String.valueOf(city.latitude))
            .putString("weather_lon", String.valueOf(city.longitude)).putBoolean("widget_weather", true).remove("weather_cache").apply();
        listener.update(null); refresh(true);
    }
    static City[] search(String name) throws Exception {
        JSONObject value = get("https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(name, "UTF-8") + "&count=5&language=fr&format=json");
        JSONArray values = value.optJSONArray("results"); if (values == null) return new City[0];
        City[] result = new City[values.length()]; for (int i = 0; i < result.length; i++) result[i] = new City(values.getJSONObject(i)); return result;
    }
    private static long degrees(double value) {
        if (!Double.isFinite(value) || value < -100 || value > 70) throw new IllegalArgumentException("Invalid temperature");
        return Math.round(value);
    }
    private static JSONObject get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Weather unavailable");
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int read;
                while ((read = stream.read(buffer)) != -1) { if (bytes.size() + read > 65536) throw new IllegalStateException("Weather response too large"); bytes.write(buffer, 0, read); }
                return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    static String description(int code) {
        if (code == 0) return "Ciel dégagé";
        if (code == 1) return "Peu nuageux";
        if (code == 2) return "Éclaircies";
        if (code == 3) return "Couvert";
        if (code == 45 || code == 48) return "Brouillard";
        if (code >= 51 && code <= 57) return "Bruine";
        if (code >= 61 && code <= 67) return "Pluie";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Neige";
        if (code >= 80 && code <= 82) return "Averses";
        if (code >= 95) return "Orage";
        return "Météo";
    }
    @Override public void close() { closed = true; generation++; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
