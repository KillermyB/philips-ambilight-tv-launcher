package ch.atelier.tv;

import android.net.Uri;
import android.os.SystemClock;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** On-demand LRCLIB lookup. No account, writes, stored history or Spotify command. */
final class Lyrics {
    static final class Line {
        final long time; final String text;
        Line(long time, String text) { this.time = time; this.text = text; }
    }
    static final class Result {
        final List<Line> lines; final boolean synced; final String status;
        Result(List<Line> lines, boolean synced, String status) { this.lines = lines; this.synced = synced; this.status = status; }
    }
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d+)\\]");
    private static final LinkedHashMap<String, Result> cache = new LinkedHashMap<String, Result>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Result> entry) { return size() > 8; }
    };
    private static long nextAllowed, lastRequest;
    static String key(MusicAccessService.Track track) { return track.title + '\n' + track.artist + '\n' + track.album + '\n' + track.duration; }
    private static Result message(String text) { return new Result(Collections.emptyList(), false, text); }
    static synchronized Result load(MusicAccessService.Track track, boolean retry) throws Exception {
        if (!track.session || track.title.isEmpty() || track.artist.isEmpty()) return message("Lancez un morceau dans Spotify, puis revenez aux paroles.");
        String key = key(track);
        if (!retry && cache.containsKey(key)) return cache.get(key);
        Result result;
        try {
            Uri.Builder address = Uri.parse("https://lrclib.net/api/get").buildUpon()
                .appendQueryParameter("track_name", track.title).appendQueryParameter("artist_name", track.artist);
            if (!track.album.isEmpty()) address.appendQueryParameter("album_name", track.album);
            if (track.duration >= 1000 && track.duration <= 3600000) address.appendQueryParameter("duration", String.valueOf(Math.round(track.duration / 1000f)));
            String exact = request(address.build());
            result = exact == null ? message("Aucune parole trouvée pour cette version du morceau.") : parse(new JSONObject(exact), track);
            // One fallback only, without the album. Never broaden the title, artist or duration.
            if (result.lines.isEmpty() && !result.status.contains("instrumental") && track.duration >= 1000 && track.duration <= 3600000) {
                String search = request(Uri.parse("https://lrclib.net/api/search").buildUpon()
                    .appendQueryParameter("track_name", track.title).appendQueryParameter("artist_name", track.artist).build());
                Result alternative = search == null ? null : select(new JSONArray(search), track);
                if (alternative != null) result = alternative;
            }
        } catch (Busy error) { return message(error.getMessage()); }
        cache.put(key, result); return result;
    }
    private static final class Busy extends IOException { Busy(String message) { super(message); } }
    private static String request(Uri address) throws Exception {
        long now = SystemClock.elapsedRealtime();
        if (now < nextAllowed) throw new Busy("LRCLIB demande une pause. Réessayez dans " + ((nextAllowed - now + 999) / 1000) + " secondes.");
        if (now - lastRequest < 350) Thread.sleep(350 - (now - lastRequest));
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        lastRequest = SystemClock.elapsedRealtime();
        HttpURLConnection connection = (HttpURLConnection)new URL(address.toString()).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(6000); connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "AtelierTV/0.13.0 (private Android TV; https://killermy.ch)");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == 429 || status == 503) {
                long seconds = 60;
                try { seconds = Math.max(1, Long.parseLong(connection.getHeaderField("Retry-After"))); } catch (Exception ignored) { }
                long current = SystemClock.elapsedRealtime();
                nextAllowed = seconds > (Long.MAX_VALUE - current - 350) / 1000 ? Long.MAX_VALUE : current + seconds * 1000L + 350;
                throw new Busy("LRCLIB est momentanément occupé. Réessayez plus tard.");
            }
            if (status == 404) return null;
            String type = connection.getContentType();
            if (status != 200 || type == null || !type.toLowerCase(Locale.ROOT).contains("application/json")) throw new IOException("Lyrics unavailable");
            if (connection.getContentLengthLong() > 256 * 1024) throw new IOException("Lyrics response too large");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (output.size() + count > 256 * 1024) throw new IOException("Lyrics response too large");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
    private static boolean matches(JSONObject record, MusicAccessService.Track track, boolean requireDuration) {
        if (!normalized(record.optString("trackName")).equals(normalized(track.title)) ||
            !normalized(record.optString("artistName")).equals(normalized(track.artist))) return false;
        double duration = record.optDouble("duration", Double.NaN);
        return track.duration > 0 ? Double.isFinite(duration) && Math.abs(duration - track.duration / 1000d) <= 2.5 : !requireDuration;
    }
    static Result select(JSONArray records, MusicAccessService.Track track) throws Exception {
        JSONObject best = null; double score = Double.NEGATIVE_INFINITY;
        if (track.duration <= 0) return null;
        for (int i = 0; i < Math.min(20, records.length()); i++) {
            JSONObject candidate = records.optJSONObject(i); if (candidate == null || !matches(candidate, track, true)) continue;
            Result parsed = parse(candidate, track);
            if (parsed.lines.isEmpty() && !candidate.optBoolean("instrumental")) continue;
            double value = (normalized(candidate.optString("albumName")).equals(normalized(track.album)) ? 40 : 0)
                + (parsed.synced ? 20 : 0) - Math.abs(candidate.getDouble("duration") - track.duration / 1000d);
            if (value > score) { best = candidate; score = value; }
        }
        return best == null ? null : parse(best, track);
    }
    static Result parse(JSONObject record, MusicAccessService.Track track) throws Exception {
        if (!matches(record, track, false))
            return message("La version trouvée ne correspond pas assez précisément au morceau.");
        if (record.optBoolean("instrumental")) return message("Ce morceau est indiqué comme instrumental sur LRCLIB.");
        String synced = record.isNull("syncedLyrics") ? "" : record.optString("syncedLyrics");
        List<Line> lines = timed(synced);
        if (!lines.isEmpty()) return new Result(lines, true, "Paroles synchronisées · LRCLIB");
        String plain = record.isNull("plainLyrics") ? "" : record.optString("plainLyrics");
        lines = new ArrayList<>();
        if (plain.length() <= 40000) for (String line : plain.split("\\r?\\n")) {
            if (lines.size() >= 400) break;
            lines.add(new Line(-1, cleanLine(line)));
        }
        return lines.isEmpty() || plain.trim().isEmpty() ? message("Les paroles de ce morceau ne sont pas encore disponibles.") :
            new Result(lines, false, "Paroles non synchronisées · LRCLIB");
    }
    static List<Line> timed(String lrc) {
        List<Line> lines = new ArrayList<>(); if (lrc.length() > 40000) return lines;
        long offset = 0; Matcher adjustment = OFFSET.matcher(lrc);
        if (adjustment.find()) try { offset = Math.max(-60000, Math.min(60000, Long.parseLong(adjustment.group(1)))); } catch (NumberFormatException ignored) { }
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher match = TIMESTAMP.matcher(raw); List<Long> times = new ArrayList<>(); int end = 0;
            while (match.find() && times.size() < 10) {
                long minute = Long.parseLong(match.group(1)), second = Long.parseLong(match.group(2));
                String fraction = match.group(3); long millis = fraction == null ? 0 : Long.parseLong((fraction + "00").substring(0, 3));
                if (second < 60 && minute <= 180) times.add(Math.max(0, minute * 60000 + second * 1000 + millis + offset)); end = match.end();
            }
            String text = cleanLine(raw.substring(end));
            for (long time : times) if (lines.size() < 400) lines.add(new Line(time, text));
        }
        lines.sort(Comparator.comparingLong(line -> line.time)); return lines;
    }
    static int indexAt(List<Line> lines, long position) {
        int index = -1;
        for (int i = 0; i < lines.size(); i++) { if (lines.get(i).time > position) break; index = i; }
        return index;
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
    private static String cleanLine(String value) {
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), 500));
    }
}
