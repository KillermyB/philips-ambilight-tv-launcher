package ch.atelier.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Bounded public artwork on the original, explicitly allowed provider hosts. */
final class AlbumArt {
    static Bitmap load(String address) throws Exception { return load(address, 768); }
    static Bitmap load(String address, int limit) throws Exception {
        return loadImage(address, limit, false);
    }
    static Bitmap loadAudioDb(String address) throws Exception { return loadImage(address, 1600, true); }
    static boolean audioDbUrl(String address) {
        try {
            URL url = new URL(address); String host = url.getHost().toLowerCase(Locale.ROOT), path = url.getPath();
            return "https".equals(url.getProtocol()) && url.getPort() == -1 && url.getUserInfo() == null && url.getQuery() == null && url.getRef() == null &&
                (host.equals("www.theaudiodb.com") || host.equals("theaudiodb.com") || host.equals("r2.theaudiodb.com")) &&
                (path.startsWith("/images/media/artist/fanart/") || path.startsWith("/images/media/artist/widethumb/") || path.startsWith("/images/media/track/mvidscreen/")) &&
                !path.contains("..") && !path.contains("%");
        } catch (Exception ignored) { return false; }
    }
    private static Bitmap loadImage(String address, int limit, boolean audioDb) throws Exception {
        limit = Math.max(300, Math.min(1600, limit));
        URL url = new URL(address); String host = url.getHost().toLowerCase(Locale.ROOT);
        if (audioDb ? !audioDbUrl(address) : !"https".equals(url.getProtocol()) || url.getPort() != -1 || url.getUserInfo() != null ||
            !(host.equals("scdn.co") || host.endsWith(".scdn.co") || host.equals("spotifycdn.com") || host.endsWith(".spotifycdn.com"))) return null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "image/*");
        try {
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > 2 * 1024 * 1024) return null;
            byte[] data;
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || bytes.size() + count > 2 * 1024 * 1024) return null;
                    bytes.write(buffer, 0, count);
                }
                data = bytes.toByteArray();
            }
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0 || (long) options.outWidth * options.outHeight > 16000000) return null;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >= limit) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false; Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap == null) return null;
            float scale = Math.min(1, (float)limit / Math.max(bitmap.getWidth(), bitmap.getHeight()));
            if (scale < 1) {
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)), Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                if (resized != bitmap) bitmap.recycle(); bitmap = resized;
            }
            return bitmap;
        } finally { connection.disconnect(); }
    }
}
