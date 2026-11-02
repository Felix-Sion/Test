package snake;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/** 每种难度保存三个独立对局的成绩；相同分数可以分别占一个名次。 */
public final class ScoreBoard {
    private final Path file;
    private final EnumMap<Difficulty, List<Integer>> scores = new EnumMap<>(Difficulty.class);
    private int legacyBest;
    private boolean dirty;
    private boolean storageError;

    public ScoreBoard(Path home) {
        file = home.resolve("data").resolve("leaderboards.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException error) {
                storageError = true;
            }
        }
        for (Difficulty difficulty : Difficulty.values()) {
            List<Integer> values = new ArrayList<>();
            for (String token : properties.getProperty(difficulty.name(), "").split(",")) {
                try {
                    int score = Integer.parseInt(token.strip());
                    if (score > 0) values.add(score);
                } catch (NumberFormatException ignored) { /* 跳过损坏的单项，保留其他有效成绩。 */ }
            }
            scores.put(difficulty, bestThree(values));
        }
        // 旧版没有记录难度，不能推断它属于哪个榜单；原文件始终保留。
        Path legacyFile = file.resolveSibling("best-score.txt");
        try {
            legacyBest = Math.max(0, Integer.parseInt(Files.readString(legacyFile).strip()));
        } catch (IOException | NumberFormatException ignored) {
            legacyBest = 0;
        }
    }

    public List<Integer> top(Difficulty difficulty) {
        return scores.get(difficulty);
    }

    public void record(Difficulty difficulty, int score) {
        if (score <= 0) return;
        List<Integer> values = new ArrayList<>(scores.get(difficulty));
        values.add(score);
        List<Integer> top = bestThree(values);
        if (!top.equals(scores.get(difficulty))) {
            scores.put(difficulty, top);
            dirty = true;
        }
    }

    public void save() throws IOException {
        if (!dirty) return;
        Files.createDirectories(file.getParent());
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        for (Difficulty difficulty : Difficulty.values()) {
            properties.setProperty(difficulty.name(), scores.get(difficulty).stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }
        Path temporary = Files.createTempFile(file.getParent(), "leaderboards-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Snake Arcade: top 3 completed rounds per difficulty");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            storageError = false;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public int legacyBest() { return legacyBest; }
    public boolean storageError() { return storageError; }

    private static List<Integer> bestThree(List<Integer> values) {
        return values.stream().sorted(Comparator.reverseOrder()).limit(3).toList();
    }
}
