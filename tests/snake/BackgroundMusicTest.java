package snake;

import javax.sound.sampled.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 音乐控制测试使用替身，不会在测试时播放声音；另检查真实内置音频。 */
public final class BackgroundMusicTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        bundledAudioIsReadable();
        loopMuteAndResume();
        muteDuringLoading();
        closeDuringLoading();
        unavailableAudioDoesNotCrash();
        System.out.println("PASS: " + checks + " background-music checks");
    }

    private static ExecutorService worker() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "music-test");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void drain(ExecutorService worker) throws Exception {
        worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    private static void bundledAudioIsReadable() throws Exception {
        var resource = BackgroundMusic.class.getResource(BackgroundMusic.RESOURCE);
        check(resource != null, "soundtrack exists on classpath");
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(resource)) {
            AudioFormat format = audio.getFormat();
            check(format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED, "native PCM playback format");
            check(format.getSampleSizeInBits() == 16 && format.getChannels() == 2, "16-bit stereo music");
            check(audio.getFrameLength() > format.getFrameRate(), "valid non-empty full soundtrack");
            byte[] buffer = new byte[8192];
            check(audio.read(buffer) > 0, "audio samples can be decoded");
        }
    }

    private static void loopMuteAndResume() throws Exception {
        ExecutorService worker = worker();
        FakeClip fake = new FakeClip();
        BackgroundMusic music = new BackgroundMusic(() -> fake.clip, () -> { }, worker);
        try {
            drain(worker);
            check(music.status() == BackgroundMusic.Status.READY, "asynchronous load completed");
            check(fake.loops == 0, "loading alone does not play in background windows");
            music.setPlaying(true);
            drain(worker);
            check(fake.running && fake.loops == 1 && fake.loopCount == Clip.LOOP_CONTINUOUSLY,
                    "music uses continuous looping");
            music.setPlaying(true);
            drain(worker);
            check(fake.loops == 1, "refreshing game does not restart music");
            music.setEnabled(false);
            drain(worker);
            check(!fake.running && !music.isEnabled(), "mute stops playback");
            music.setEnabled(true);
            drain(worker);
            check(fake.running && fake.loops == 2 && fake.rewinds == 0, "unmute resumes without rewinding");
            music.setPlaying(false);
            drain(worker);
            check(!fake.running && music.isEnabled(), "pause/focus loss does not change mute preference");
            music.setPlaying(true);
            drain(worker);
            check(fake.running && fake.loops == 3, "resume reinstates continuous looping");
        } finally { music.close(); }
        check(worker.awaitTermination(5, TimeUnit.SECONDS), "audio worker shuts down");
        check(fake.closed && fake.closes == 1, "closing releases audio device once");
        music.close();
        music.setPlaying(true);
        music.setEnabled(false);
        check(music.status() == BackgroundMusic.Status.CLOSED && fake.loops == 3,
                "late UI callbacks cannot restart closed player");
    }

    private static void muteDuringLoading() throws Exception {
        ExecutorService worker = worker();
        CountDownLatch loaded = new CountDownLatch(1);
        FakeClip fake = new FakeClip();
        BackgroundMusic music = new BackgroundMusic(() -> {
            if (!loaded.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout");
            return fake.clip;
        }, () -> { }, worker);
        try {
            music.setPlaying(true);
            music.setEnabled(false);
            loaded.countDown();
            drain(worker);
            check(music.status() == BackgroundMusic.Status.READY && fake.loops == 0,
                    "muting before load prevents a burst of sound");
            music.setEnabled(true);
            drain(worker);
            check(fake.running && fake.loops == 1, "loaded muted music can be enabled");
        } finally { loaded.countDown(); music.close(); }
        worker.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void closeDuringLoading() throws Exception {
        ExecutorService worker = worker();
        CountDownLatch loaded = new CountDownLatch(1);
        FakeClip fake = new FakeClip();
        BackgroundMusic music = new BackgroundMusic(() -> {
            if (!loaded.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout");
            return fake.clip;
        }, () -> { }, worker);
        music.setPlaying(true);
        music.close();
        loaded.countDown();
        check(worker.awaitTermination(5, TimeUnit.SECONDS), "close during load finishes");
        check(fake.closed && fake.loops == 0 && fake.closes == 1,
                "closing during load releases clip without playing");
    }

    private static void unavailableAudioDoesNotCrash() throws Exception {
        ExecutorService worker = worker();
        AtomicInteger notices = new AtomicInteger();
        try (BackgroundMusic music = new BackgroundMusic(() -> {
            throw new LineUnavailableException("no audio device");
        }, notices::incrementAndGet, worker)) {
            music.setPlaying(true);
            drain(worker);
            check(music.status() == BackgroundMusic.Status.UNAVAILABLE, "missing device gives unavailable status");
            check(notices.get() > 0, "UI is notified of missing audio");
            music.setEnabled(false);
            music.setEnabled(true);
            drain(worker);
            check(music.status() == BackgroundMusic.Status.UNAVAILABLE, "audio failure does not propagate to game");
        }
        worker.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static final class FakeClip implements InvocationHandler {
        final Clip clip = (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(),
                new Class<?>[]{Clip.class}, this);
        volatile boolean running;
        volatile boolean closed;
        volatile int loops;
        volatile int loopCount;
        volatile int rewinds;
        volatile int closes;

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "isRunning", "isActive" -> running;
                case "isOpen" -> !closed;
                case "loop" -> { loopCount = (Integer) args[0]; loops++; running = true; yield null; }
                case "stop" -> { running = false; yield null; }
                case "close" -> { running = false; closed = true; closes++; yield null; }
                case "setFramePosition", "setMicrosecondPosition" -> { rewinds++; yield null; }
                case "toString" -> "FakeClip";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
