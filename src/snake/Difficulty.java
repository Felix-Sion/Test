package snake;

/** 稳定的存档标识与游戏速度；每局开始后固定难度。 */
public enum Difficulty {
    RELAXED("悠闲", 170), NORMAL("标准", 125), FAST("挑战", 90);

    final String title;
    final int delay;

    Difficulty(String title, int delay) {
        this.title = title;
        this.delay = delay;
    }

    @Override public String toString() { return title; }
}
