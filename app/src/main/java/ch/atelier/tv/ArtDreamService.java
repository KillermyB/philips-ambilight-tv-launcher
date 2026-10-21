package ch.atelier.tv;

import android.service.dreams.DreamService;

public final class ArtDreamService extends DreamService {
    private ArtPresentation art;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow(); setInteractive(false); setFullscreen(true);
        setScreenBright(getSharedPreferences("atelier", MODE_PRIVATE).getBoolean("dream_bright", false));
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted(); art = new ArtPresentation(this); setContentView(art.view);
    }
    @Override public void onDreamingStopped() { stopArt(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { stopArt(); super.onDetachedFromWindow(); }
    private void stopArt() { if (art != null) { art.stop(); art = null; } }
}
