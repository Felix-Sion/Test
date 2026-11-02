package snake;

import java.util.List;
import java.util.Random;
import static snake.GameModel.Direction.*;
import static snake.GameModel.State.*;

/** 不依赖第三方测试框架：失败时抛出 AssertionError，返回非零退出码。 */
public final class GameModelTest {
    private static int checks;

    public static void main(String[] args) {
        initialStateAndPause();
        directionBuffer();
        foodAndGrowth();
        collisionWithWall();
        movingIntoVacatedTail();
        collisionWithBody();
        foodNeverOverlapsSnake();
        resetAfterGameOver();
        System.out.println("PASS: " + checks + " game-rule checks");
    }

    private static GameModel emptyCornerFood() {
        return new GameModel(12, 12, new Random(0) {
            @Override public int nextInt(int bound) { return 0; }
        });
    }

    private static void initialStateAndPause() {
        GameModel g = emptyCornerFood();
        check(g.state() == READY && g.snake().size() == 4 && g.score() == 0, "initial state");
        List<GameModel.Cell> before = g.snake();
        g.tick();
        check(g.snake().equals(before), "ready does not move");
        g.start();
        g.tick();
        check(g.snake().getFirst().equals(new GameModel.Cell(7, 6)), "moves one cell");
        g.togglePause();
        before = g.snake();
        g.requestDirection(UP);
        g.tick();
        check(g.state() == PAUSED && g.snake().equals(before), "pause freezes movement");
        g.togglePause();
        g.tick();
        check(g.direction() == RIGHT && g.snake().getFirst().x() == 8, "resume and ignore paused input");
    }

    private static void directionBuffer() {
        GameModel g = emptyCornerFood();
        g.start();
        g.requestDirection(LEFT);
        g.tick();
        check(g.direction() == RIGHT, "reject direct reversal");
        g.requestDirection(UP);
        g.requestDirection(LEFT);
        g.requestDirection(DOWN); // 缓冲区满时，不再塞入更多方向。
        g.tick();
        check(g.direction() == UP && g.state() == RUNNING, "first buffered turn");
        g.tick();
        check(g.direction() == LEFT && g.state() == RUNNING, "second buffered turn");
        g.tick();
        check(g.direction() == LEFT, "buffer is bounded");
    }

    private static GameModel foodAhead() {
        return new GameModel(12, 12, new Random(0) {
            private boolean first = true;
            @Override public int nextInt(int bound) {
                if (first) { first = false; return 75; } // 初始蛇头右侧的空格 (7, 6)。
                return 0;
            }
        });
    }

    private static void foodAndGrowth() {
        GameModel g = foodAhead();
        check(g.food().equals(new GameModel.Cell(7, 6)), "deterministic food setup");
        g.start();
        g.tick();
        check(g.score() == 10 && g.snake().size() == 5, "food grows snake and adds 10 points");
        check(g.food() != null && !g.snake().contains(g.food()), "new food is on empty cell");
    }

    private static void collisionWithWall() {
        GameModel g = emptyCornerFood();
        g.start();
        for (int i = 0; i < 6; i++) g.tick();
        check(g.state() == GAME_OVER, "wall collision ends game");
        List<GameModel.Cell> last = g.snake();
        g.tick();
        check(g.snake().equals(last), "ended game does not keep moving");
        check(g.snake().stream().allMatch(c -> c.x() >= 0 && c.x() < 12 && c.y() >= 0 && c.y() < 12),
                "collision never puts a cell outside the board");
    }

    private static void movingIntoVacatedTail() {
        GameModel g = emptyCornerFood();
        g.start();
        g.tick();
        step(g, DOWN);
        step(g, LEFT);
        GameModel.Cell oldTail = g.snake().getLast();
        step(g, UP);
        check(g.state() == RUNNING && g.snake().getFirst().equals(oldTail),
                "moving into the vacated tail is legal");
    }

    private static void collisionWithBody() {
        GameModel g = foodAhead();
        g.start();
        g.tick();
        step(g, DOWN);
        step(g, LEFT);
        step(g, UP);
        check(g.state() == GAME_OVER, "non-tail body collision ends game");
    }

    private static void foodNeverOverlapsSnake() {
        GameModel g = new GameModel(8, 8, new Random(42));
        for (int i = 0; i < 100; i++) {
            g.reset();
            check(g.food() != null && !g.snake().contains(g.food()), "food placement " + i);
        }
    }

    private static void resetAfterGameOver() {
        GameModel g = foodAhead();
        g.start();
        for (int i = 0; i < 6; i++) g.tick();
        g.reset();
        check(g.state() == READY && g.score() == 0 && g.snake().size() == 4, "restart resets round");
        g.start();
        g.tick();
        check(g.state() == RUNNING && g.direction() == RIGHT, "restart clears old turns");
    }

    private static void step(GameModel g, GameModel.Direction direction) {
        g.requestDirection(direction);
        g.tick();
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
