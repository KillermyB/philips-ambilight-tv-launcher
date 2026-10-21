package ch.atelier.tv;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Personal RSS reader: fixed official feeds, eight headlines, no article scraping or tracking. */
final class News implements AutoCloseable {
    static final long REFRESH_MS = 30 * 60 * 1000L, CACHE_MS = 12 * 60 * 60 * 1000L;
    static final class Source {
        final String key, category, name, feed, host;
        Source(String key, String category, String name, String feed, String host) {
            this.key = key; this.category = category; this.name = name; this.feed = feed; this.host = host;
        }
    }
    static final Source[] SOURCES = {
        new Source("swiss", "Suisse", "RTS", "https://www.rts.ch/info/suisse/?format=rss/news", "www.rts.ch"),
        new Source("world", "International", "France 24", "https://www.france24.com/fr/rss", "www.france24.com"),
        new Source("tech", "Tech", "Le Monde · Pixels", "https://www.lemonde.fr/pixels/rss_full.xml", "www.lemonde.fr"),
        new Source("culture", "Culture", "RTS Culture", "https://www.rts.ch/info/culture/?format=rss/news", "www.rts.ch")
    };
    static final class Item {
        final Source source;
        final String title, summary, link;
        final long published, fetched;
        Item(Source source, String title, String summary, String link, long published, long fetched) throws Exception {
            URL url = new URL(link);
            if (!"https".equals(url.getProtocol()) || !source.host.equals(url.getHost()) || url.getUserInfo() != null || url.getPort() != -1 ||
                title.isEmpty() || published <= 0 || published > System.currentTimeMillis() + 2 * 60 * 60 * 1000L) throw new IllegalArgumentException("Invalid news item");
            this.source = source; this.title = title; this.summary = summary; this.link = link; this.published = published; this.fetched = fetched;
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("title", title).put("summary", summary).put("link", link).put("published", published).put("fetched", fetched);
        }
        boolean usable() {
            long now = System.currentTimeMillis();
            return now - fetched < CACHE_MS && now >= fetched - 60 * 60 * 1000L && now - published < 7 * 24 * 60 * 60 * 1000L;
        }
        String credit() { return source.category + " · " + source.name; }
        String date() { return new SimpleDateFormat("d MMM · HH:mm", Locale.FRENCH).format(new Date(published)); }
    }
    interface Listener { void update(List<Item> items, String status); }
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Item> items = new ArrayList<>();
    private boolean visible, requesting, closed;
    private long lastAttempt;
    private final Runnable tick = () -> { if (visible && !closed) { refresh(false); schedule(); } };

    News(SharedPreferences prefs, Listener listener) {
        this.prefs = prefs; this.listener = listener;
        try {
            JSONObject cache = new JSONObject(prefs.getString("news_cache", "{}"));
            for (Source source : SOURCES) {
                JSONArray values = cache.optJSONArray(source.key); if (values == null) continue;
                for (int i = 0; i < Math.min(values.length(), 2); i++) try {
                    JSONObject v = values.getJSONObject(i);
                    Item item = new Item(source, clean(v.optString("title"), 240), clean(v.optString("summary"), 600), v.getString("link"), v.getLong("published"), v.getLong("fetched"));
                    if (item.usable()) items.add(item);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        publish();
    }
    boolean enabled(Source source) { return prefs.getBoolean("news_" + source.key, true); }
    List<Item> selection() {
        List<Item> selected = new ArrayList<>(); Set<String> links = new HashSet<>();
        // Alternate subjects before showing each subject's second headline.
        for (int rank = 0; rank < 2; rank++) for (Source source : SOURCES) if (enabled(source)) {
            int index = 0;
            for (Item item : items) if (item.source == source && item.usable()) {
                if (index++ == rank && links.add(item.link)) selected.add(item);
            }
        }
        return selected;
    }
    private void publish() {
        List<Item> selected = selection(); int enabled = 0, available = 0; boolean old = false;
        for (Source source : SOURCES) if (enabled(source)) {
            enabled++; for (Item item : selected) if (item.source == source) { available++; break; }
        }
        for (Item item : selected) old |= System.currentTimeMillis() - item.fetched > 90 * 60 * 1000L;
        listener.update(selected, enabled == 0 ? "Choisissez vos rubriques dans Réglages" : selected.isEmpty() ? "Actualités momentanément indisponibles" :
            (old ? "Dernière sélection enregistrée" : "Sélection de " + selected.size() + " titres") + (available < enabled ? " · " + available + "/" + enabled + " sources" : ""));
    }
    void setVisible(boolean value) {
        boolean changed = visible != value; visible = value; handler.removeCallbacks(tick);
        if (!visible || closed) return;
        if (changed) publish(); refresh(false); schedule();
    }
    void preferencesChanged() { publish(); }
    private void schedule() { handler.removeCallbacks(tick); if (visible && !closed) handler.postDelayed(tick, REFRESH_MS); }
    void refresh(boolean force) {
        if (closed || requesting || (!force && System.currentTimeMillis() - lastAttempt < (items.isEmpty() ? 2 * 60 * 1000L : REFRESH_MS))) return;
        requesting = true; lastAttempt = System.currentTimeMillis();
        final List<Item> previous = new ArrayList<>(items); final Set<String> enabled = new HashSet<>();
        for (Source source : SOURCES) if (enabled(source)) enabled.add(source.key);
        worker.execute(() -> {
            List<Item> received = new ArrayList<>();
            for (Source source : SOURCES) {
                if (Thread.currentThread().isInterrupted()) return;
                List<Item> fresh = null;
                if (enabled.contains(source.key)) try { fresh = fetch(source); } catch (Exception ignored) { }
                if (fresh != null && !fresh.isEmpty()) received.addAll(fresh);
                else for (Item item : previous) if (item.source == source && item.usable()) received.add(item);
            }
            handler.post(() -> {
                requesting = false; if (closed) return;
                items.clear(); items.addAll(received);
                try {
                    JSONObject cache = new JSONObject();
                    for (Source source : SOURCES) { JSONArray values = new JSONArray(); for (Item item : items) if (item.source == source) values.put(item.json()); cache.put(source.key, values); }
                    prefs.edit().putString("news_cache", cache.toString()).apply();
                } catch (Exception ignored) { }
                publish();
            });
        });
    }
    static List<Item> parse(Source source, byte[] bytes, long fetched) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new ByteArrayInputStream(bytes), null);
        List<Item> values = new ArrayList<>(); String title = "", summary = "", link = "", date = ""; boolean inItem = false;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.DOCDECL || event == XmlPullParser.ENTITY_REF) throw new IllegalArgumentException("Unsupported XML entities");
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equals(name)) { inItem = true; title = summary = link = date = ""; }
                else if (inItem && ("title".equals(name) || "description".equals(name) || "link".equals(name) || "pubDate".equals(name))) {
                    String value = parser.nextText();
                    if ("title".equals(name)) title = clean(value, 240);
                    else if ("description".equals(name)) summary = clean(value, 600);
                    else if ("link".equals(name)) link = value.trim(); else date = value.trim();
                }
            } else if (event == XmlPullParser.END_TAG && "item".equals(parser.getName()) && inItem) {
                inItem = false;
                try { Item item = new Item(source, title, summary, link, parseDate(date), fetched); if (item.usable()) values.add(item); } catch (Exception ignored) { }
            }
        }
        Collections.sort(values, (a, b) -> Long.compare(b.published, a.published));
        List<Item> result = new ArrayList<>(); Set<String> links = new HashSet<>();
        for (Item item : values) if (links.add(item.link)) { result.add(item); if (result.size() == 2) break; }
        return result;
    }
    private static long parseDate(String value) throws Exception {
        for (String pattern : new String[]{"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z"}) try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US); format.setLenient(false); return format.parse(value).getTime();
        } catch (Exception ignored) { }
        throw new IllegalArgumentException("Missing publication date");
    }
    private static String clean(String value, int maximum) {
        return ArtStore.clean(Html.fromHtml(value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "").replaceAll("(?is)<img\\b[^>]*>", ""), Html.FROM_HTML_MODE_COMPACT).toString().replaceAll("[\\s\\u00a0]+", " "), "", maximum);
    }
    private static List<Item> fetch(Source source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source.feed).openConnection();
        connection.setConnectTimeout(6000); connection.setReadTimeout(6000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml"); connection.setRequestProperty("User-Agent", "AtelierTV/0.6 (personal RSS reader)");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("RSS unavailable");
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int read;
                while ((read = stream.read(buffer)) != -1) { if (bytes.size() + read > 512 * 1024) throw new IllegalStateException("RSS too large"); bytes.write(buffer, 0, read); }
                return parse(source, bytes.toByteArray(), System.currentTimeMillis());
            }
        } finally { connection.disconnect(); }
    }
    @Override public void close() { closed = true; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
