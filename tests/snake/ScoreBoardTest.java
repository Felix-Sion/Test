package snake;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public final class ScoreBoardTest {
    private static int checks;
    private static Path root;

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory(Path.of("out"), "leaderboard-tests-");
        rankingAndPersistence();
        tolerateBadEntries();
        preserveLegacy();
        roundBoundaries();
        difficultyLock();
        restartAndClose();
        retryFailedSave();
        System.out.println("PASS: " + checks + " leaderboard/session checks");
    }

    private static void rankingAndPersistence() throws Exception {
        Path home = root.resolve("ranking");
        ScoreBoard board = new ScoreBoard(home);
        for (int score : new int[]{40, 120, 80, 20, 200}) board.record(Difficulty.RELAXED, score);
        board.record(Difficulty.NORMAL, 50);
        board.record(Difficulty.FAST, 30);
        check(board.top(Difficulty.RELAXED).equals(List.of(200, 120, 80)), "top three descending");
        check(board.top(Difficulty.NORMAL).equals(List.of(50)), "normal independent");
        check(board.top(Difficulty.FAST).equals(List.of(30)), "fast independent");
        board.record(Difficulty.NORMAL, 50);
        board.record(Difficulty.NORMAL, 0);
        board.record(Difficulty.NORMAL, -10);
        check(board.top(Difficulty.NORMAL).equals(List.of(50, 50)), "ties count but zero/negative excluded");
        try {
            board.top(Difficulty.NORMAL).add(999);
            throw new AssertionError("scores can be mutated externally");
        } catch (UnsupportedOperationException expected) { checks++; }
        board.save();
        ScoreBoard restored = new ScoreBoard(home);
        for (Difficulty difficulty : Difficulty.values()) {
            check(restored.top(difficulty).equals(board.top(difficulty)), "reload " + difficulty);
        }
    }

    private static void tolerateBadEntries() throws Exception {
        Path home = root.resolve("partial");
        Files.createDirectories(home.resolve("data"));
        Files.writeString(home.resolve("data/leaderboards.properties"),
                "version=1\nRELAXED=bad,10,-4,300,90,200\nNORMAL=0,nope,20\nFAST=\n");
        ScoreBoard board = new ScoreBoard(home);
        check(board.top(Difficulty.RELAXED).equals(List.of(300, 200, 90)), "skip bad entries and trim to three");
        check(board.top(Difficulty.NORMAL).equals(List.of(20)), "other difficulty remains readable");
        check(board.top(Difficulty.FAST).isEmpty(), "empty difficulty has no invented score");
    }

    private static void preserveLegacy() throws Exception {
        Path home = root.resolve("legacy");
        Files.createDirectories(home.resolve("data"));
        Path legacy = home.resolve("data/best-score.txt");
        Files.writeString(legacy, "170");
        ScoreBoard board = new ScoreBoard(home);
        check(board.legacyBest() == 170, "old record is available as history");
        for (Difficulty difficulty : Difficulty.values()) {
            check(board.top(difficulty).isEmpty(), "do not guess legacy difficulty");
        }
        board.record(Difficulty.FAST, 20);
        board.save();
        check(Files.readString(legacy).equals("170"), "old file is untouched");
        check(new ScoreBoard(home).legacyBest() == 170, "history survives new-format save");
    }

    private static GameModel foodAhead() {
        return new GameModel(12, 12, new Random(0) {
            private boolean first = true;
            @Override public int nextInt(int bound) {
                if (first) { first = false; return 75; }
                return 0;
            }
        });
    }

    private static void roundBoundaries() {
        ScoreBoard board = new ScoreBoard(root.resolve("round"));
        GameSession session = new GameSession(foodAhead(), board);
        session.setDifficulty(Difficulty.FAST);
        session.togglePlay();
        session.tick();
        check(board.top(Difficulty.FAST).isEmpty(), "no intermediate per-food records");
        for (int i = 0; i < 5; i++) session.tick();
        check(board.top(Difficulty.FAST).equals(List.of(10)), "finished round assigned correctly");
        for (int i = 0; i < 10; i++) session.tick();
        session.finishRound();
        check(board.top(Difficulty.FAST).equals(List.of(10)), "no duplicate for same round");
        check(board.top(Difficulty.NORMAL).isEmpty(), "other difficulty unchanged");
    }

    private static void difficultyLock() {
        ScoreBoard board = new ScoreBoard(root.resolve("locked"));
        GameSession session = new GameSession(foodAhead(), board);
        check(session.setDifficulty(Difficulty.RELAXED), "select before start");
        session.turn(GameModel.Direction.RIGHT);
        session.tick();
        check(!session.setDifficulty(Difficulty.FAST), "lock while running");
        session.togglePlay();
        check(!session.setDifficulty(Difficulty.NORMAL), "lock while paused");
        session.finishRound();
        check(board.top(Difficulty.RELAXED).equals(List.of(10)), "paused round assigned to starting difficulty");
        check(session.difficulty() == Difficulty.RELAXED, "rejected change has no effect");
    }

    private static void restartAndClose() {
        ScoreBoard board = new ScoreBoard(root.resolve("restart"));
        GameSession session = new GameSession(foodAhead(), board);
        session.togglePlay();
        session.tick();
        session.restart();
        check(board.top(Difficulty.NORMAL).equals(List.of(10)), "restart submits old positive score");
        session.finishRound();
        session.finishRound();
        check(board.top(Difficulty.NORMAL).equals(List.of(10)), "zero next round and repeated close ignored");
        GameSession next = new GameSession(foodAhead(), board);
        next.setDifficulty(Difficulty.FAST);
        next.togglePlay();
        for (int i = 0; i < 6; i++) next.tick();
        check(next.setDifficulty(Difficulty.RELAXED), "can change after round ends");
        next.finishRound();
        check(board.top(Difficulty.FAST).equals(List.of(10)), "changing next difficulty does not move old score");
        check(board.top(Difficulty.RELAXED).isEmpty(), "new selection has no fake result");
    }

    private static void retryFailedSave() throws Exception {
        Path home = root.resolve("retry");
        ScoreBoard board = new ScoreBoard(home);
        GameSession session = new GameSession(foodAhead(), board);
        Files.createDirectories(home);
        Path blocker = home.resolve("data");
        Files.writeString(blocker, "not a directory");
        session.togglePlay();
        session.tick();
        session.finishRound();
        check(session.saveFailed(), "save error is reported");
        check(board.top(Difficulty.NORMAL).equals(List.of(10)), "failed save keeps score in memory");
        Files.move(blocker, home.resolve("blocker-backup.txt"));
        session.finishRound();
        check(!session.saveFailed(), "retry succeeds");
        check(new ScoreBoard(home).top(Difficulty.NORMAL).equals(List.of(10)), "retry saves once");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
