package io.github.chrehor36.neonnoir;

import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.view.Surface;

/** Debug builds only: fake inputs that own the test channels. Tuning one shows "no video", which is expected. */
public abstract class DemoTvInputService extends TvInputService {
    public static final class Antenna extends DemoTvInputService {
    }

    public static final class Free extends DemoTvInputService {
    }

    @Override
    public Session onCreateSession(String inputId) {
        return new Session(this) {
            @Override
            public void onRelease() {
            }

            @Override
            public boolean onSetSurface(Surface surface) {
                return true;
            }

            @Override
            public void onSetStreamVolume(float volume) {
            }

            @Override
            public boolean onTune(Uri channelUri) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                return true;
            }

            @Override
            public void onSetCaptionEnabled(boolean enabled) {
            }
        };
    }
}
