package snake;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Executes real patched machine code, including concurrent readers/writers. */
public final class NativeColorPatchTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        boolean required = args.length > 0 && args[0].equals("--require-native");
        Path home = (args.length > 1 ? Path.of(args[1]) : Path.of(".")).toAbsolutePath();
        NativeColorPatch missing = new NativeColorPatch(home.resolve("out/missing-native-library"));
        check(!missing.available(), "a missing DLL disables only the demonstration");
        check(missing.colorRgb() == NativeColorPatch.ORIGINAL_RGB, "missing DLL retains original color");
        check(!missing.toggle() && missing.snapshot() == null && !missing.error().isBlank(),
                "missing DLL provides diagnostics without attempting a native call");

        NativeColorPatch patch = new NativeColorPatch(home);
        if (!patch.available()) {
            check(!required, "native DLL is required: " + patch.error());
            System.out.println("PASS: " + checks + " native fallback checks (DLL not built)");
            return;
        }
        Path library = home.resolve(NativeColorPatch.LIBRARY);
        byte[] diskBefore = digest(library);
        try {
            check(patch.setApplied(false), "original mode can be requested");
            NativeColorPatch.Snapshot before = patch.snapshot();
            byte[] original = before.entryBytes();
            check(before.processId() == ProcessHandle.current().pid(), "DLL is in this JVM process");
            check(!before.applied() && patch.colorRgb() == NativeColorPatch.ORIGINAL_RGB,
                    "executing original native function returns mint");
            check(Arrays.equals(Arrays.copyOf(original, 4), new byte[]{(byte) 0xf3, 0x0f, 0x1e, (byte) 0xfa}),
                    "original landing pad is ENDBR64");
            boolean nops = true;
            for (int i = 4; i < original.length; i++) nops &= original[i] == (byte) 0x90;
            check(nops, "the entire reserved patch area initially contains NOPs");
            byte[] exported = before.entryBytes();
            exported[0] = 0;
            check(before.entryBytes()[0] == (byte) 0xf3, "snapshot owns its byte array");
            print("ORIGINAL", before, patch.colorRgb());

            check(patch.setApplied(true), "patch write succeeds");
            NativeColorPatch.Snapshot after = patch.snapshot();
            byte[] code = after.entryBytes();
            check(after.applied() && patch.colorRgb() == NativeColorPatch.PATCHED_RGB,
                    "executing the SAME native entry point now returns purple");
            check(after.targetAddress() == before.targetAddress() && after.processId() == before.processId(),
                    "target address and PID remain unchanged");
            check(Arrays.equals(Arrays.copyOf(code, 4), Arrays.copyOf(original, 4)), "ENDBR64 is preserved");
            check(code[4] == (byte) 0xff && code[5] == 0x25 && code[6] == 0 && code[7] == 0
                    && code[8] == 0 && code[9] == 0, "actual bytes encode JMP [RIP+0]");
            long destination = ByteBuffer.wrap(code, 10, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
            check(destination == after.patchAddress(), "embedded address is the real replacement function");
            check(code[18] == (byte) 0x90 && code[19] == (byte) 0x90, "remaining patch padding is intact");
            check((after.protection() & 0xff) == 0x20, "page protection is restored to execute/read");
            check(patch.setApplied(true) && Arrays.equals(code, patch.snapshot().entryBytes()),
                    "applying an existing patch is idempotent");
            print("PATCHED", after, patch.colorRgb());

            check(patch.setApplied(false), "restoring succeeds");
            check(Arrays.equals(original, patch.snapshot().entryBytes()), "all original entry bytes are restored");
            check(patch.colorRgb() == NativeColorPatch.ORIGINAL_RGB, "restored instructions execute original logic");
            check(patch.toggle() && patch.snapshot().applied(), "toggle applies the patch");
            check(patch.toggle() && !patch.snapshot().applied(), "toggle restores the patch");
            concurrentExecution(patch);
            check(patch.setApplied(false) && Arrays.equals(original, patch.snapshot().entryBytes()),
                    "entry bytes remain restorable after concurrent stress");
            check(Arrays.equals(diskBefore, digest(library)), "on-disk DLL is not modified");
            print("RESTORED", patch.snapshot(), patch.colorRgb());
            System.out.println("PASS: " + checks + " native inline-patch checks");
        } finally {
            patch.setApplied(false);
        }
    }

    private static void concurrentExecution(NativeColorPatch patch) throws Exception {
        try (var pool = Executors.newFixedThreadPool(5)) {
            CyclicBarrier start = new CyclicBarrier(5);
            var tasks = new ArrayList<Future<?>>();
            for (int reader = 0; reader < 4; reader++) {
                tasks.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < 25_000; i++) {
                        int rgb = patch.colorRgb();
                        if (rgb != NativeColorPatch.ORIGINAL_RGB && rgb != NativeColorPatch.PATCHED_RGB) {
                            throw new AssertionError("reader observed an invalid native result: " + rgb);
                        }
                    }
                    return null;
                }));
            }
            tasks.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < 2_000; i++) {
                    if (!patch.setApplied((i & 1) == 0)) throw new AssertionError(patch.error());
                }
                return null;
            }));
            for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
            check(true, "100,000 native calls coexist with 2,000 patch/restore operations");
        }
    }

    private static byte[] digest(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }

    private static void print(String stage, NativeColorPatch.Snapshot snapshot, int color) {
        System.out.printf("%s pid=%d entry=0x%X rgb=%06X protect=0x%X bytes=%s%n", stage,
                snapshot.processId(), snapshot.targetAddress(), color, snapshot.protection(),
                HexFormat.ofDelimiter(" ").withUpperCase().formatHex(snapshot.entryBytes()));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
