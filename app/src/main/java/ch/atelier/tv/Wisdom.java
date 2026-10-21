package ch.atelier.tv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Calendar;

final class Wisdom {
    private final JSONArray quotes;
    Wisdom(Context context) {
        try (InputStream stream = context.getAssets().open("home-quotes.json")) {
            quotes = new JSONArray(ArtStore.readText(stream));
        } catch (Exception error) { throw new IllegalStateException("Citation library is missing", error); }
    }
    JSONObject today(int offset) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return quotes.optJSONObject(Math.floorMod(day + offset, quotes.length()));
    }
}
