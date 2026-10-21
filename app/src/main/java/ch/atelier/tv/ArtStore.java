package ch.atelier.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class ArtStore {
    static final long MAX_UPLOAD = 20L * 1024 * 1024;
    static final int MAX_CUSTOM = 60;
    final Context context;
    final SharedPreferences prefs;
    final File directory;
    private JSONObject notices = new JSONObject();
    private JSONObject interpretations = new JSONObject();

    static final class Art {
        final String id, title, artist, year, asset, description, source;
        final File file;
        final float cropY;
        Art(JSONObject value, File directory) throws Exception {
            id = value.getString("id"); title = value.optString("title", "Mon tableau");
            artist = value.optString("artist", "Collection personnelle"); year = value.optString("year", "");
            asset = value.optString("asset", "");
            cropY = Math.max(0, Math.min(1, (float) value.optDouble("cropY", .5)));
            description = value.optString("description", ""); source = value.optString("source", "");
            String name = value.optString("file", "");
            file = name.matches("user-[a-f0-9-]+\\.img") ? new File(directory, name) : null;
            if (asset.isEmpty() && (file == null || !file.isFile())) throw new IOException("Image introuvable");
        }
        boolean personal() { return file != null; }
        String credit() { return artist + (year.isEmpty() ? "" : " · " + year); }
    }

    ArtStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("atelier", Context.MODE_PRIVATE);
        directory = new File(this.context.getFilesDir(), "paintings"); directory.mkdirs();
        try (InputStream stream = this.context.getAssets().open("art/notices.json")) { notices = new JSONObject(readText(stream)); }
        catch (Exception error) { Log.w("AtelierTV", "Artwork notices unavailable", error); }
        try (InputStream stream = this.context.getAssets().open("art/interpretations.json")) { interpretations = new JSONObject(readText(stream)); }
        catch (Exception error) { Log.w("AtelierTV", "Artwork interpretations unavailable", error); }
    }

    String interpretation(Art work) {
        if (work.personal()) return "";
        JSONObject item = interpretations.optJSONObject(work.id);
        return item == null ? "" : item.optString("text") + "\n\nPour prolonger la réflexion\n" + item.optString("question");
    }

    synchronized List<Art> list() {
        List<Art> result = new ArrayList<>();
        try (InputStream stream = context.getAssets().open("art/catalog.json")) {
            JSONArray values = new JSONArray(readText(stream));
            for (int i = 0; i < values.length(); i++) result.add(new Art(values.getJSONObject(i), directory));
        } catch (Exception error) { Log.e("AtelierTV", "Bundled artwork unavailable", error); }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            List<File> ordered = new ArrayList<>(); Collections.addAll(ordered, files);
            Collections.sort(ordered, Comparator.comparing(File::getName));
            for (File file : ordered) try (InputStream stream = new FileInputStream(file)) {
                result.add(new Art(new JSONObject(readText(stream)), directory));
            } catch (Exception error) { Log.w("AtelierTV", "Skipping incomplete artwork", error); }
        }
        return result;
    }

    Art selected() {
        List<Art> values = list(); String id = prefs.getString("art", "sierra");
        for (Art art : values) if (art.id.equals(id)) return art;
        return values.isEmpty() ? null : values.get(0);
    }
    String notes(Art work) { return prefs.getString("art_notes_" + work.id, notices.optString(work.id, work.description)); }
    void notes(Art work, String value) { prefs.edit().putString("art_notes_" + work.id, clean(value, "", 500)).apply(); }
    void resetNotes(Art work) { prefs.edit().remove("art_notes_" + work.id).apply(); }

    synchronized Art add(File upload, String title, String artist) throws Exception {
        int count = 0; for (Art art : list()) if (art.personal()) count++;
        if (count >= MAX_CUSTOM) throw new IOException("La galerie contient déjà 60 tableaux personnels.");
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(upload.getAbsolutePath(), bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 30000 || bounds.outHeight > 30000 ||
            (long) bounds.outWidth * bounds.outHeight > 100000000L ||
            !("image/jpeg".equals(bounds.outMimeType) || "image/png".equals(bounds.outMimeType) || "image/webp".equals(bounds.outMimeType)))
            throw new IOException("Choisissez une image JPEG, PNG ou WebP valide (100 mégapixels maximum).");
        String id = "user-" + UUID.randomUUID();
        File image = new File(directory, id + ".img");
        if (!upload.renameTo(image)) throw new IOException("Impossible d’enregistrer le tableau.");
        JSONObject value = new JSONObject().put("id", id).put("file", image.getName())
            .put("title", clean(title, "Mon tableau", 100)).put("artist", clean(artist, "Collection personnelle", 100));
        File metadata = new File(directory, id + ".json");
        try (FileOutputStream stream = new FileOutputStream(metadata)) {
            stream.write(value.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { image.delete(); metadata.delete(); throw error; }
        Art art = new Art(value, directory); prefs.edit().putString("art", id).apply(); return art;
    }

    synchronized void remove(Art art) {
        if (!art.personal()) return;
        File metadata = new File(directory, art.id + ".json");
        if (metadata.delete()) art.file.delete();
        if (art.id.equals(prefs.getString("art", ""))) prefs.edit().putString("art", "sierra").apply();
    }

    /** Screen rendition; preserves proportions and the curated subject within a 16:9 crop. */
    Bitmap decodeCover(Art art, int maxDimension) throws IOException {
        Bitmap original = decode(art, maxDimension);
        float ratio = 16f / 9;
        int width = original.getWidth(), height = original.getHeight();
        int cropWidth = Math.min(width, Math.round(height * ratio));
        int cropHeight = Math.min(height, Math.round(width / ratio));
        Bitmap crop = Bitmap.createBitmap(original, (width - cropWidth) / 2,
            Math.round((height - cropHeight) * art.cropY), cropWidth, cropHeight);
        if (crop != original) original.recycle();
        int outputWidth = Math.min(1920, maxDimension);
        if (crop.getWidth() > outputWidth) {
            Bitmap resized = Bitmap.createScaledBitmap(crop, outputWidth, Math.round(outputWidth / ratio), true);
            if (resized != crop) crop.recycle(); return resized;
        }
        return crop;
    }

    Bitmap decode(Art art, int maxDimension) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        try (InputStream stream = open(art)) { BitmapFactory.decodeStream(stream, null, options); }
        int sample = 1;
        while (Math.max(options.outWidth, options.outHeight) / (sample * 2) >= maxDimension) sample *= 2;
        options.inJustDecodeBounds = false; options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = open(art)) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
        if (bitmap == null) throw new IOException("Image illisible.");
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest > maxDimension) {
            float scale = maxDimension / (float) longest;
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            if (resized != bitmap) bitmap.recycle(); bitmap = resized;
        }
        if (art.personal()) try {
            ExifInterface exif = new ExifInterface(art.file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix transform = new Matrix();
            switch (orientation) {
                case 2: transform.setScale(-1, 1); break;
                case 3: transform.setRotate(180); break;
                case 4: transform.setScale(1, -1); break;
                case 5: transform.setRotate(90); transform.postScale(-1, 1); break;
                case 6: transform.setRotate(90); break;
                case 7: transform.setRotate(270); transform.postScale(-1, 1); break;
                case 8: transform.setRotate(270); break;
                default: break;
            }
            if (!transform.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transform, true);
                if (rotated != bitmap) bitmap.recycle(); bitmap = rotated;
            }
        } catch (IOException ignored) { }
        return bitmap;
    }

    private InputStream open(Art art) throws IOException {
        return art.file != null ? new FileInputStream(art.file) : context.getAssets().open(art.asset);
    }
    static String readText(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > 1024 * 1024) throw new IOException("Fichier trop volumineux.");
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }
    static String clean(String value, String fallback, int maximum) {
        String text = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return text.isEmpty() ? fallback : text.substring(0, Math.min(maximum, text.length()));
    }
}
