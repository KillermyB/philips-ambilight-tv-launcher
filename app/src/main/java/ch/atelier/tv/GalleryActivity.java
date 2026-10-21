package ch.atelier.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

public final class GalleryActivity extends Activity {
    private ArtPresentation art;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        art = new ArtPresentation(this); setContentView(art.view);
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { art.next(1); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) { art.next(-1); return true; }
        return super.onKeyDown(code, event);
    }
    @Override protected void onStart() {
        super.onStart(); if (art == null) { art = new ArtPresentation(this); setContentView(art.view); }
    }
    @Override protected void onStop() { if (art != null) { art.stop(); art = null; } super.onStop(); }
    @Override protected void onDestroy() { if (art != null) art.stop(); super.onDestroy(); }
}
