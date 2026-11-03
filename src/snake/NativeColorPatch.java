package snake;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Locale;

/** A controlled Windows x64 inline patch in our own JNI DLL, never JVM code. */
public final class NativeColorPatch {
    public static final int ORIGINAL_RGB = 0x63edb4;
    public static final int PATCHED_RGB = 0xbb86fc;
    public static final Path LIBRARY = Path.of("native", "windows-x64", "snakepatch.dll");
    private final boolean available;
    private String error = "";

    public NativeColorPatch(Path home) {
        boolean ready = false;
        String architecture = System.getProperty("os.arch", "");
        if (!System.getProperty("os.name", "").startsWith("Windows")
                || !(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            error = "原生补丁演示需要 Windows x64；游戏仍可正常游玩。";
        } else {
            Path library = home.resolve(LIBRARY).toAbsolutePath().normalize();
            if (!Files.isRegularFile(library)) {
                error = "未找到 " + library + "；请运行 build-native.cmd。";
            } else {
                try {
                    System.load(library.toString());
                    int result = nativeInitialize();
                    ready = result == 0;
                    if (!ready) error = "原生入口检查失败，Windows 错误码：" + result;
                } catch (UnsatisfiedLinkError | SecurityException failure) {
                    error = "原生 DLL 无法加载：" + failure.getMessage();
                }
            }
        }
        available = ready;
    }

    public boolean available() { return available; }
    public synchronized String error() { return error; }

    /** The actual color comes from executing the original native entry point. */
    public int colorRgb() {
        return available ? nativeColor() : ORIGINAL_RGB;
    }

    public synchronized boolean setApplied(boolean applied) {
        if (!available) return false;
        int result = nativeSetApplied(applied);
        error = result == 0 ? "" : "写入或恢复补丁失败，Windows 错误码：" + result;
        return result == 0;
    }

    public synchronized boolean toggle() {
        return available && setApplied(!snapshot().applied());
    }

    public Snapshot snapshot() {
        if (!available) return null;
        long[] raw = nativeSnapshot();
        byte[] bytes = new byte[20];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (raw[5 + i / 8] >>> (i % 8 * 8));
        }
        return new Snapshot(raw[0], raw[1], raw[2], raw[3] != 0, (int) raw[4], bytes);
    }

    public String describe() {
        Snapshot current = snapshot();
        if (current == null) return error();
        return String.format(Locale.ROOT, """
                进程 PID：%d（应用和恢复补丁时保持不变）
                DLL：%s
                原函数入口：0x%016X
                实际补丁位置：0x%016X（保留前 4 字节 ENDBR64）
                补丁函数入口：0x%016X
                当前状态：%s
                当前页面保护：0x%X（正常为 0x20，即只读 / 可执行）

                当前入口的 20 字节：
                %s

                原始入口：F3 0F 1E FA，随后 16 个 90（NOP）
                补丁入口：F3 0F 1E FA FF 25 00 00 00 00 [8 字节目标地址] 90 90
                FF 25 00 00 00 00：JMP QWORD PTR [RIP+0]
                后续 8 字节：补丁函数地址，按小端序保存。

                F8：应用 / 恢复。修改发生在本进程的原生 DLL 内存中。
                """, current.processId(), LIBRARY, current.targetAddress(), current.targetAddress() + 4,
                current.patchAddress(), current.applied() ? "已应用 · 紫色" : "原始 · 薄荷绿",
                current.protection(), HexFormat.ofDelimiter(" ").withUpperCase().formatHex(current.entryBytes()));
    }

    public record Snapshot(long processId, long targetAddress, long patchAddress,
                           boolean applied, int protection, byte[] entryBytes) {
        public Snapshot { entryBytes = entryBytes.clone(); }
        @Override public byte[] entryBytes() { return entryBytes.clone(); }
    }

    private static native int nativeInitialize();
    private static native int nativeColor();
    private static native int nativeSetApplied(boolean applied);
    private static native long[] nativeSnapshot();
}
