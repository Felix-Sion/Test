package snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** 独立于界面的游戏规则，方便阅读和测试。 */
public final class GameModel {
    public record Cell(int x, int y) { }

    public enum Direction {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        final int dx;
        final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        boolean isOpposite(Direction other) {
            return dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    public enum State { READY, RUNNING, PAUSED, GAME_OVER, WON }

    private final int columns;
    private final int rows;
    private final Random random;
    private final Deque<Cell> snake = new ArrayDeque<>();
    private final Deque<Direction> turns = new ArrayDeque<>();
    private Direction direction;
    private Cell food;
    private State state;
    private int score;

    public GameModel(int columns, int rows) {
        this(columns, rows, new Random());
    }

    GameModel(int columns, int rows, Random random) {
        if (columns < 8 || rows < 8) {
            throw new IllegalArgumentException("棋盘至少需要 8 × 8 个格子");
        }
        this.columns = columns;
        this.rows = rows;
        this.random = Objects.requireNonNull(random);
        reset();
    }

    public void reset() {
        snake.clear();
        turns.clear();
        for (int i = 0; i < 4; i++) {
            snake.addLast(new Cell(columns / 2 - i, rows / 2));
        }
        direction = Direction.RIGHT;
        score = 0;
        state = State.READY;
        placeFood();
    }

    public void start() {
        if (state == State.READY) state = State.RUNNING;
    }

    public void togglePause() {
        if (state == State.RUNNING) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.RUNNING;
    }

    /** 最多缓存两次转向；与上一次已接受的方向比较，防止快速按键导致掉头。 */
    public void requestDirection(Direction next) {
        Objects.requireNonNull(next);
        if (state != State.RUNNING || turns.size() >= 2) return;
        Direction previous = turns.isEmpty() ? direction : turns.getLast();
        if (next != previous && !next.isOpposite(previous)) turns.addLast(next);
    }

    public void tick() {
        if (state != State.RUNNING) return;
        if (!turns.isEmpty()) direction = turns.removeFirst();

        Cell head = snake.getFirst();
        Cell next = new Cell(head.x() + direction.dx, head.y() + direction.dy);
        boolean growing = next.equals(food);
        boolean outside = next.x() < 0 || next.y() < 0
                || next.x() >= columns || next.y() >= rows;
        // 没吃到食物时，尾巴会在本步移走，因此允许进入原来的尾巴格。
        boolean hitsBody = snake.contains(next)
                && (growing || !next.equals(snake.getLast()));
        if (outside || hitsBody) {
            state = State.GAME_OVER;
            return;
        }

        if (!growing) snake.removeLast();
        snake.addFirst(next);
        if (growing) {
            score += 10;
            if (snake.size() == columns * rows) {
                food = null;
                state = State.WON;
            } else {
                placeFood();
            }
        }
    }

    private void placeFood() {
        // 从空格中选择，避免蛇很长时不断重试甚至无法结束。
        List<Cell> free = new ArrayList<>(columns * rows - snake.size());
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                Cell cell = new Cell(x, y);
                if (!snake.contains(cell)) free.add(cell);
            }
        }
        food = free.isEmpty() ? null : free.get(random.nextInt(free.size()));
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public int score() { return score; }
    public State state() { return state; }
    public Direction direction() { return direction; }
    public Cell food() { return food; }
    public List<Cell> snake() { return List.copyOf(snake); }
}
