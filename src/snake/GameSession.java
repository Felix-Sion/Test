package snake;

import java.io.IOException;

/** 管理一局的边界，保证一个成绩只登记一次，并归入开局时的难度。 */
public final class GameSession {
    private final GameModel game;
    private final ScoreBoard scores;
    private Difficulty difficulty = Difficulty.NORMAL;
    private Difficulty roundDifficulty = Difficulty.NORMAL;
    private boolean submitted;
    private boolean saveFailed;

    public GameSession(GameModel game, ScoreBoard scores) {
        this.game = game;
        this.scores = scores;
        saveFailed = scores.storageError();
    }

    public boolean setDifficulty(Difficulty next) {
        if (roundInProgress()) return false;
        difficulty = next;
        return true;
    }

    public void togglePlay() {
        switch (game.state()) {
            case READY -> begin();
            case RUNNING, PAUSED -> game.togglePause();
            case GAME_OVER, WON -> restart();
        }
    }

    public void turn(GameModel.Direction direction) {
        if (game.state() == GameModel.State.READY) begin();
        game.requestDirection(direction);
    }

    public void restart() {
        finishRound();
        game.reset();
        begin();
    }

    public void tick() {
        game.tick();
        if (game.state() == GameModel.State.GAME_OVER || game.state() == GameModel.State.WON) {
            finishRound();
        }
    }

    /** 游戏结束、手动重开或关闭窗口时结算；0 分不入榜。 */
    public void finishRound() {
        if (game.state() != GameModel.State.READY && !submitted) {
            scores.record(roundDifficulty, game.score());
            submitted = true;
        }
        try {
            scores.save();
            saveFailed = scores.storageError();
        } catch (IOException error) {
            saveFailed = true;
        }
    }

    private void begin() {
        roundDifficulty = difficulty;
        submitted = false;
        game.start();
    }

    public boolean roundInProgress() {
        return game.state() == GameModel.State.RUNNING || game.state() == GameModel.State.PAUSED;
    }

    public Difficulty difficulty() { return difficulty; }
    public boolean saveFailed() { return saveFailed; }
}
