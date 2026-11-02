package snake;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 后台载入内置音频，控制循环、暂停和静音，不阻塞 Swing 界面。 */
public final class BackgroundMusic implements AutoCloseable {
    static final String RESOURCE = "/audio/background.wav";
    public enum Status { LOADING, READY, UNAVAILABLE, CLOSED }

    @FunctionalInterface
    interface ClipLoader { Clip load() throws Exception; }

    private final Object lock = new Object();
    private final ExecutorService worker;
    private final Runnable changed;
    private Clip clip;
    private boolean enabled = true;
    private boolean playingRequested;
    private boolean unavailable;
    private boolean closed;

    public BackgroundMusic(Runnable changed) {
        this(BackgroundMusic::openBundledClip, changed, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "snake-background-music");
            thread.setDaemon(true);
            return thread;
        }));
    }

    BackgroundMusic(ClipLoader loader, Runnable changed, ExecutorService worker) {
        this.changed = Objects.requireNonNull(changed);
        this.worker = Objects.requireNonNull(worker);
        worker.execute(() -> load(loader));
    }

    public void setPlaying(boolean playing) {
        synchronized (lock) {
            if (closed || playingRequested == playing) return;
            playingRequested = playing;
            worker.execute(this::updatePlayback);
        }
    }

    public void setEnabled(boolean enabled) {
        synchronized (lock) {
            if (closed || this.enabled == enabled) return;
            this.enabled = enabled;
            worker.execute(this::updatePlayback);
        }
    }

    public boolean isEnabled() {
        synchronized (lock) { return enabled; }
    }

    public Status status() {
        synchronized (lock) {
            if (closed) return Status.CLOSED;
            if (unavailable) return Status.UNAVAILABLE;
            return clip == null ? Status.LOADING : Status.READY;
        }
    }

    private void load(ClipLoader loader) {
        try {
            Clip loaded = Objects.requireNonNull(loader.load());
            synchronized (lock) {
                if (closed) {
                    release(loaded);
                    return;
                }
                clip = loaded;
                applyPlayback();
            }
        } catch (Exception error) {
            markUnavailable();
        }
        changed.run();
    }

    private void updatePlayback() {
        try {
            synchronized (lock) {
                if (closed) return;
                applyPlayback();
            }
        } catch (RuntimeException error) {
            markUnavailable();
            changed.run();
        }
    }

    /** 必须持有 lock；停止后保留当前位置，恢复时继续循环。 */
    private void applyPlayback() {
        if (clip == null || unavailable) return;
        if (enabled && playingRequested) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else if (clip.isRunning()) {
            clip.stop();
        }
    }

    private void markUnavailable() {
        synchronized (lock) {
            if (closed) return;
            unavailable = true;
            if (clip != null) {
                release(clip);
                clip = null;
            }
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            worker.execute(() -> {
                synchronized (lock) {
                    if (clip != null) {
                        release(clip);
                        clip = null;
                    }
                }
            });
            worker.shutdown();
        }
    }

    static Clip openBundledClip() throws Exception {
        InputStream resource = BackgroundMusic.class.getResourceAsStream(RESOURCE);
        if (resource == null) throw new IOException("Background music resource is missing");
        try (InputStream buffered = new BufferedInputStream(resource);
                AudioInputStream audio = AudioSystem.getAudioInputStream(buffered)) {
            Clip opened = AudioSystem.getClip();
            try {
                opened.open(audio);
                if (opened.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) opened.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), -12.0f)));
                }
                return opened;
            } catch (Exception error) {
                release(opened);
                throw error;
            }
        }
    }

    private static void release(Clip clip) {
        try { clip.stop(); } catch (RuntimeException ignored) { }
        try { clip.close(); } catch (RuntimeException ignored) { }
    }
}
