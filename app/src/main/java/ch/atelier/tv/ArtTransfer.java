package ch.atelier.tv;

import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A temporary, PIN-protected LAN upload session. Never runs as a background service. */
final class ArtTransfer implements Closeable {
    final String pin, address;
    private final String token, host;
    private final ArtStore store;
    private final ServerSocket server;
    private final ThreadPoolExecutor clients = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(4), new ThreadPoolExecutor.AbortPolicy());
    private final Set<Socket> active = Collections.synchronizedSet(new HashSet<>());
    private final long created = SystemClock.elapsedRealtime();
    private final Runnable changed;
    private volatile boolean closed;
    private int failures;
    private long failureWindow = created;

    ArtTransfer(ArtStore store, Runnable changed) throws IOException {
        this.store = store; this.changed = changed;
        String ip = localAddress();
        if (ip == null) throw new IOException("Connectez la TV au même réseau local que votre téléphone ou Mac.");
        server = new ServerSocket(8787, 4, InetAddress.getByName("0.0.0.0"));
        host = ip + ":" + server.getLocalPort(); address = "http://" + host;
        SecureRandom random = new SecureRandom(); pin = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        byte[] secret = new byte[24]; random.nextBytes(secret);
        token = Base64.encodeToString(secret, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        Thread thread = new Thread(this::accept, "Atelier-art-transfer"); thread.setDaemon(true); thread.start();
    }

    private void accept() {
        while (!closed) try {
            Socket socket = server.accept(); socket.setSoTimeout(10000);
            if (!socket.getInetAddress().isSiteLocalAddress() && !socket.getInetAddress().isLoopbackAddress()) { socket.close(); continue; }
            active.add(socket);
            try { clients.execute(() -> handle(socket)); }
            catch (RuntimeException busy) { active.remove(socket); socket.close(); }
        } catch (IOException error) { if (!closed) android.util.Log.w("AtelierTV", "Transfer socket closed", error); }
    }

    private void handle(Socket socket) {
        try (Socket connection = socket) {
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = connection.getOutputStream();
            try {
                String first = line(input); String[] request = first.split(" ");
                if (request.length != 3 || !request[2].startsWith("HTTP/1.")) throw new IOException("Requête invalide.");
                Map<String, String> headers = new HashMap<>(); int headerSize = first.length();
                while (true) {
                    String item = line(input); headerSize += item.length();
                    if (headerSize > 16384) throw new IOException("En-tête trop volumineux.");
                    if (item.isEmpty()) break;
                    int separator = item.indexOf(':'); if (separator < 1) throw new IOException("En-tête invalide.");
                    String name = item.substring(0, separator).toLowerCase(Locale.ROOT);
                    if (headers.put(name, item.substring(separator + 1).trim()) != null) throw new IOException("En-tête dupliqué.");
                }
                if (!host.equals(headers.get("host"))) { json(output, 403, new JSONObject().put("error", "Utilisez l’adresse affichée sur la TV.")); return; }
                String origin = headers.get("origin");
                if (origin != null && !address.equals(origin)) { json(output, 403, new JSONObject().put("error", "Origine refusée.")); return; }
                if (SystemClock.elapsedRealtime() - created > 15 * 60 * 1000L) { json(output, 403, new JSONObject().put("error", "Session terminée. Rouvrez l’import sur la TV.")); return; }
                String method = request[0], path = request[1];
                if ("GET".equals(method) && "/".equals(path)) {
                    try (InputStream page = store.context.getAssets().open("transfer.html")) {
                        respond(output, 200, "text/html; charset=utf-8", ArtStore.readText(page).getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }
                if ("GET".equals(method) && "/favicon.ico".equals(path)) { respond(output, 204, "text/plain", new byte[0]); return; }
                if ("GET".equals(method) && "/inter.ttf".equals(path)) {
                    try (InputStream font = store.context.getAssets().open("fonts/inter.ttf")) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                        while ((count = font.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        respond(output, 200, "font/ttf", bytes.toByteArray());
                    }
                    return;
                }
                if (!"POST".equals(method)) { json(output, 405, new JSONObject().put("error", "Méthode indisponible.")); return; }
                if (headers.containsKey("transfer-encoding")) throw new IOException("Envoi fragmenté non pris en charge.");
                long length;
                try { length = Long.parseLong(headers.getOrDefault("content-length", "-1")); }
                catch (NumberFormatException invalid) { throw new IOException("Taille invalide."); }
                if (length < 1 || length > ArtStore.MAX_UPLOAD) { json(output, 413, new JSONObject().put("error", "20 Mo maximum par image.")); return; }
                if ("/api/unlock".equals(path)) {
                    if (length > 1024) throw new IOException("Code invalide.");
                    String entered = new JSONObject(body(input, length, 1024)).optString("pin");
                    if (!unlock(entered)) { json(output, 403, new JSONObject().put("error", "Code incorrect ou trop de tentatives. Réessayez dans une minute.")); return; }
                    json(output, 200, new JSONObject().put("token", token)); return;
                }
                String supplied = headers.getOrDefault("x-atelier-token", "");
                if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    json(output, 403, new JSONObject().put("error", "Saisissez le code affiché sur la TV.")); return;
                }
                if ("/api/artwork".equals(path)) {
                    File temporary = new File(store.context.getCacheDir(), "upload-" + UUID.randomUUID());
                    try {
                        try (FileOutputStream file = new FileOutputStream(temporary)) { copy(input, file, length); }
                        String title = URLDecoder.decode(headers.getOrDefault("x-art-title", "Mon tableau"), "UTF-8");
                        String artist = URLDecoder.decode(headers.getOrDefault("x-art-artist", "Collection personnelle"), "UTF-8");
                        ArtStore.Art art = store.add(temporary, title, artist); changed.run();
                        json(output, 200, new JSONObject().put("id", art.id).put("title", art.title));
                    } finally { temporary.delete(); }
                } else if ("/api/widgets".equals(path)) {
                    JSONObject values = new JSONObject(body(input, length, 4096));
                    android.content.SharedPreferences.Editor edit = store.prefs.edit();
                    for (String key : new String[]{"widget_quote", "widget_clock", "widget_date", "widget_art", "widget_note", "widget_weather", "widget_music", "widget_news"})
                        if (values.has(key)) edit.putBoolean(key, values.getBoolean(key));
                    if (values.has("note")) edit.putString("note", ArtStore.clean(values.getString("note"), "", 100));
                    edit.apply(); changed.run(); json(output, 200, new JSONObject().put("ok", true));
                } else if ("/api/artwork/info".equals(path)) {
                    JSONObject values = new JSONObject(body(input, length, 4096));
                    String id = values.getString("id"); ArtStore.Art found = null;
                    for (ArtStore.Art work : store.list()) if (work.id.equals(id)) { found = work; break; }
                    if (found == null) throw new IOException("Tableau introuvable.");
                    if (values.optBoolean("reset", false)) store.resetNotes(found); else store.notes(found, values.getString("notes"));
                    changed.run(); json(output, 200, new JSONObject().put("ok", true));
                } else if ("/api/slideshow".equals(path)) {
                    JSONObject values = new JSONObject(body(input, length, 4096));
                    int seconds = values.optInt("home_seconds", store.prefs.getInt("home_seconds", 60));
                    if (seconds != 30 && seconds != 60 && seconds != 180 && seconds != 300 && seconds != 600)
                        throw new IOException("Choisissez une durée proposée pour le diaporama.");
                    if (values.optBoolean("home_personal", store.prefs.getBoolean("home_personal", false))) {
                        boolean personal = false; for (ArtStore.Art work : store.list()) if (work.personal()) personal = true;
                        if (!personal) throw new IOException("Ajoutez d’abord un tableau personnel dans Galerie.");
                    }
                    android.content.SharedPreferences.Editor edit = store.prefs.edit().putInt("home_seconds", seconds);
                    for (String key : new String[]{"home_slideshow", "home_shuffle", "home_personal"})
                        if (values.has(key)) edit.putBoolean(key, values.getBoolean(key));
                    edit.apply(); changed.run(); json(output, 200, new JSONObject().put("ok", true));
                } else if ("/api/library".equals(path)) {
                    body(input, length, 4096); JSONArray works = new JSONArray();
                    for (ArtStore.Art art : store.list()) works.put(new JSONObject().put("id", art.id).put("title", art.title).put("artist", art.artist)
                        .put("year", art.year).put("notes", store.notes(art)).put("interpretation", store.interpretation(art)).put("notes_custom", store.prefs.contains("art_notes_" + art.id)).put("personal", art.personal()));
                    json(output, 200, new JSONObject().put("works", works).put("note", store.prefs.getString("note", ""))
                        .put("widget_quote", store.prefs.getBoolean("widget_quote", true))
                        .put("widget_note", store.prefs.getBoolean("widget_note", false))
                        .put("widget_clock", store.prefs.getBoolean("widget_clock", true))
                        .put("widget_date", store.prefs.getBoolean("widget_date", true))
                        .put("widget_art", store.prefs.getBoolean("widget_art", true))
                        .put("widget_weather", store.prefs.getBoolean("widget_weather", true))
                        .put("widget_music", store.prefs.getBoolean("widget_music", true))
                        .put("widget_news", store.prefs.getBoolean("widget_news", true))
                        .put("selected", store.prefs.getString("art", "sierra"))
                        .put("home_slideshow", store.prefs.getBoolean("home_slideshow", true))
                        .put("home_shuffle", store.prefs.getBoolean("home_shuffle", true))
                        .put("home_personal", store.prefs.getBoolean("home_personal", false))
                        .put("home_seconds", store.prefs.getInt("home_seconds", 60)));
                } else if ("/api/select".equals(path)) {
                    String id = new JSONObject(body(input, length, 4096)).optString("id"); boolean found = false;
                    for (ArtStore.Art art : store.list()) if (art.id.equals(id)) { store.prefs.edit().putString("art", id).apply(); found = true; break; }
                    if (!found) throw new IOException("Tableau introuvable.");
                    changed.run(); json(output, 200, new JSONObject().put("ok", true));
                } else json(output, 404, new JSONObject().put("error", "Action introuvable."));
            } catch (Exception error) {
                json(output, 400, new JSONObject().put("error", error instanceof IOException ? error.getMessage() : "L’envoi n’a pas pu être terminé."));
            }
        } catch (Exception ignored) { } finally { active.remove(socket); }
    }

    private synchronized boolean unlock(String value) {
        long now = SystemClock.elapsedRealtime();
        if (now - failureWindow > 60000) { failures = 0; failureWindow = now; }
        if (failures >= 5) return false;
        boolean accepted = MessageDigest.isEqual(pin.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
        if (!accepted) failures++; return accepted;
    }
    private String line(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int value;
        while ((value = stream.read()) != -1) {
            if (value == '\n') return bytes.toString("US-ASCII").replace("\r", "");
            if (bytes.size() >= 8192) throw new IOException("En-tête trop long."); bytes.write(value);
        }
        throw new IOException("Connexion interrompue.");
    }
    private String body(InputStream input, long length, int maximum) throws IOException {
        if (length > maximum) throw new IOException("Données trop volumineuses.");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); copy(input, output, length); return output.toString("UTF-8");
    }
    private void copy(InputStream input, OutputStream output, long length) throws IOException {
        byte[] bytes = new byte[16384]; long left = length;
        while (left > 0) { int count = input.read(bytes, 0, (int) Math.min(left, bytes.length)); if (count < 0) throw new IOException("Envoi interrompu."); output.write(bytes, 0, count); left -= count; }
    }
    private void json(OutputStream output, int status, JSONObject value) throws IOException {
        respond(output, status, "application/json; charset=utf-8", value.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void respond(OutputStream output, int status, String type, byte[] bytes) throws IOException {
        String headers = "HTTP/1.1 " + status + " " + (status < 300 ? "OK" : "Error") + "\r\nContent-Type: " + type +
            "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff" +
            "\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' blob:; frame-ancestors 'none'\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII)); output.write(bytes); output.flush();
    }
    static String localAddress() throws IOException {
        String fallback = null;
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback()) continue;
            for (InetAddress ip : Collections.list(network.getInetAddresses())) if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) {
                if (network.getName().startsWith("wlan") || network.getName().startsWith("eth")) return ip.getHostAddress();
                fallback = ip.getHostAddress();
            }
        }
        return fallback;
    }
    @Override public void close() {
        closed = true; try { server.close(); } catch (IOException ignored) { }
        synchronized (active) { for (Socket socket : active) try { socket.close(); } catch (IOException ignored) { } active.clear(); }
        clients.shutdownNow();
    }
}
