package io.github.chrehor36.neonnoir;

import android.service.dreams.DreamService;

/** Art Mode as the system screensaver (the same screen as ArtActivity). Any key wakes the TV back to where it was. */
public class ArtDream extends DreamService {
    private ArtView art;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        art = new ArtView(this);
        setContentView(art);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        art.start();
    }

    @Override
    public void onDreamingStopped() {
        art.stop();
        super.onDreamingStopped();
    }
}
