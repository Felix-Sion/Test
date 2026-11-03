package snake;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.*;
import java.util.List;

/** Swing 游戏界面；可选的原生 DLL 用于演示可恢复的机器码热补丁。 */
public final class SnakeGame extends JPanel {
    private static final Color BACKGROUND = new Color(12, 18, 30);
    private static final Color SURFACE = new Color(20, 30, 47);
    private static final Color TEXT = new Color(234, 242, 250);
    private static final Color MUTED = new Color(139, 158, 181);
    private static final Color MINT = new Color(99, 237, 180);
    private static final Color CORAL = new Color(255, 121, 117);
    private static final String FONT = "Microsoft YaHei UI";

    private final GameModel game = new GameModel(30, 20);
    private final ScoreBoard scores;
    private final GameSession session;
    private final JLabel scoreValue = valueLabel();
    private final JLabel difficultyValue = label("标准", 23, TEXT);
    private final JLabel[][] rankValues = new JLabel[Difficulty.values().length][3];
    private final JLabel[] rankTitles = new JLabel[Difficulty.values().length];
    private final JLabel status = label("", 12, MUTED);
    private final JButton play = new ArcadeButton("开始游戏", true);
    private final JButton restart = new ArcadeButton("重新开始", false);
    private final JButton musicToggle = new ArcadeButton("音乐：开", false);
    private final JButton patchToggle = new ArcadeButton("应用补丁 [F8]", false);
    private final JLabel patchStatus = label("", 12, MUTED);
    private final JComboBox<Difficulty> speed = new JComboBox<>(Difficulty.values());
    private final Board board = new Board();
    private final Timer timer;
    private final BackgroundMusic music;
    private final NativeColorPatch nativePatch;
    private boolean windowFocused;
    private SnakeGame(Path home) {
        scores = new ScoreBoard(home);
        session = new GameSession(game, scores);
        nativePatch = new NativeColorPatch(home);
        music = new BackgroundMusic(() -> SwingUtilities.invokeLater(this::refreshMusicButton));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        setLayout(new BorderLayout(0, 18));
        add(createHeader(), BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        timer = new Timer(125, event -> advance());
        timer.setCoalesce(true);
        play.addActionListener(event -> togglePlay());
        restart.addActionListener(event -> newGame());
        musicToggle.addActionListener(event -> toggleMusic());
        patchToggle.addActionListener(event -> toggleNativePatch());
        speed.addActionListener(event -> {
            Difficulty selected = (Difficulty) speed.getSelectedItem();
            if (!session.setDifficulty(selected) && selected != session.difficulty()) {
                speed.setSelectedItem(session.difficulty());
            }
            refresh();
        });
        bindKeys();
        refresh();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("ComboBox.background", SURFACE);
            UIManager.put("ComboBox.foreground", TEXT);
            UIManager.put("ComboBox.selectionBackground", new Color(35, 63, 73));
            UIManager.put("ComboBox.selectionForeground", MINT);
            SnakeGame content = new SnakeGame(findHome());
            JFrame window = new JFrame("贪吃蛇 · Snake Arcade");
            window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            window.setContentPane(content);
            window.setMinimumSize(new Dimension(760, 700));
            window.pack();
            window.setLocationRelativeTo(null);
            window.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowGainedFocus(WindowEvent event) {
                    content.windowFocused = true;
                    content.refreshMusicPlayback();
                }
                @Override public void windowLostFocus(WindowEvent event) {
                    content.windowFocused = false;
                    if (content.game.state() == GameModel.State.RUNNING) {
                        content.session.togglePlay();
                    }
                    content.refresh();
                }
            });
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    content.session.finishRound();
                    content.nativePatch.setApplied(false);
                    content.music.close();
                }
            });
            window.setVisible(true);
        });
    }

    private JPanel createHeader() {
        JPanel header = transparent(new BorderLayout(0, 12));
        JPanel summary = transparent(new BorderLayout(18, 0));
        JPanel title = transparent(null);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(label("S N A K E   /   A R C A D E  0 1", 11, MINT));
        title.add(Box.createVerticalStrut(3));
        JLabel heading = label("贪吃蛇", 30, TEXT);
        heading.setFont(new Font(FONT, Font.BOLD, 30));
        title.add(heading);
        title.add(Box.createVerticalStrut(3));
        title.add(label("吃掉能量，保持生长。", 12, MUTED));
        summary.add(title, BorderLayout.WEST);

        JPanel stats = transparent(new GridLayout(1, 2, 10, 0));
        stats.add(statCard("本局得分", scoreValue, MINT));
        stats.add(statCard("本局难度", difficultyValue, TEXT));
        summary.add(stats, BorderLayout.EAST);
        header.add(summary, BorderLayout.NORTH);

        JPanel rankings = transparent(new GridLayout(1, 3, 10, 0));
        for (Difficulty difficulty : Difficulty.values()) {
            JPanel card = new RoundedPanel();
            card.setLayout(new BorderLayout(0, 5));
            card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            JLabel headingLabel = label(difficulty.title + " · 前三名", 12, MINT);
            rankTitles[difficulty.ordinal()] = headingLabel;
            card.add(headingLabel, BorderLayout.NORTH);
            JPanel rows = transparent(new GridLayout(3, 1, 0, 2));
            for (int rank = 0; rank < 3; rank++) {
                JPanel row = transparent(new BorderLayout());
                row.add(label("第 " + (rank + 1) + " 名", 11, MUTED), BorderLayout.WEST);
                JLabel value = label("—", 12, TEXT);
                rankValues[difficulty.ordinal()][rank] = value;
                row.add(value, BorderLayout.EAST);
                rows.add(row);
            }
            card.add(rows, BorderLayout.CENTER);
            rankings.add(card);
        }
        JPanel records = transparent(new BorderLayout(0, 5));
        records.add(rankings, BorderLayout.CENTER);
        if (scores.legacyBest() > 0) {
            records.add(label("旧版综合纪录：" + scores.legacyBest()
                    + " 分（未标注难度，仅保留为历史记录）", 10, MUTED), BorderLayout.SOUTH);
        }
        header.add(records, BorderLayout.CENTER);
        return header;
    }

    private JPanel statCard(String name, JLabel value, Color color) {
        JPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 4));
        card.setPreferredSize(new Dimension(112, 82));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 9, 16));
        card.add(label(name, 11, MUTED), BorderLayout.NORTH);
        value.setForeground(color);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel createFooter() {
        JPanel footer = transparent(new BorderLayout(0, 10));
        JPanel actions = transparent(new BorderLayout());
        JPanel left = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(label("游戏难度  ", 12, MUTED));
        speed.setSelectedItem(Difficulty.NORMAL);
        speed.setFont(new Font(FONT, Font.PLAIN, 12));
        speed.setPreferredSize(new Dimension(105, 36));
        speed.setFocusable(false);
        speed.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                JLabel item = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                item.setBackground(selected ? new Color(35, 63, 73) : SURFACE);
                item.setForeground(selected ? MINT : TEXT);
                item.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
                return item;
            }
        });
        left.add(speed);
        actions.add(left, BorderLayout.WEST);
        JPanel buttons = transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.add(musicToggle);
        buttons.add(restart);
        buttons.add(play);
        actions.add(buttons, BorderLayout.EAST);
        footer.add(actions, BorderLayout.NORTH);

        JPanel patchRow = transparent(new BorderLayout(8, 0));
        patchRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        patchRow.add(patchStatus, BorderLayout.CENTER);
        JPanel patchButtons = transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton patchDetails = new ArcadeButton("查看机器码", false);
        patchDetails.addActionListener(event -> showPatchDetails());
        patchButtons.add(patchDetails);
        patchButtons.add(patchToggle);
        patchRow.add(patchButtons, BorderLayout.EAST);
        footer.add(patchRow, BorderLayout.CENTER);

        JPanel help = transparent(new GridLayout(2, 1, 0, 5));
        help.add(label("方向键 / WASD 移动   ·   空格 暂停   ·   Enter 开始   ·   R 重开   ·   M 音乐", 12, MUTED));
        help.add(status);
        footer.add(help, BorderLayout.SOUTH);
        return footer;
    }

    private void bindKeys() {
        bind("UP", () -> turn(GameModel.Direction.UP));
        bind("W", () -> turn(GameModel.Direction.UP));
        bind("DOWN", () -> turn(GameModel.Direction.DOWN));
        bind("S", () -> turn(GameModel.Direction.DOWN));
        bind("LEFT", () -> turn(GameModel.Direction.LEFT));
        bind("A", () -> turn(GameModel.Direction.LEFT));
        bind("RIGHT", () -> turn(GameModel.Direction.RIGHT));
        bind("D", () -> turn(GameModel.Direction.RIGHT));
        bind("SPACE", this::togglePlay);
        bind("ENTER", this::togglePlay);
        bind("R", this::newGame);
        bind("M", this::toggleMusic);
        bind("F8", this::toggleNativePatch);
    }

    private void bind(String key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void turn(GameModel.Direction direction) {
        session.turn(direction);
        refresh();
    }

    private void togglePlay() {
        session.togglePlay();
        refresh();
    }

    private void newGame() {
        session.restart();
        timer.restart();
        refresh();
    }

    private void advance() {
        session.tick();
        refresh();
    }

    private void toggleMusic() {
        music.setEnabled(!music.isEnabled());
        refreshMusicButton();
    }

    private void toggleNativePatch() {
        nativePatch.toggle();
        refreshNativePatch();
        board.repaint();
    }

    private void refreshNativePatch() {
        NativeColorPatch.Snapshot current = nativePatch.snapshot();
        boolean applied = current != null && current.applied();
        patchToggle.setEnabled(nativePatch.available());
        patchToggle.setText(applied ? "恢复原始 [F8]" : "应用补丁 [F8]");
        patchStatus.setText(!nativePatch.error().isEmpty() ? "原生补丁：不可用，点击查看机器码了解原因"
                : applied ? "原生热补丁：已应用 · 紫色" : "原生热补丁：原始 · 薄荷绿");
        patchStatus.setForeground(applied ? new Color(NativeColorPatch.PATCHED_RGB) : MUTED);
        patchStatus.setToolTipText(nativePatch.error().isEmpty()
                ? "F8 修改 / 恢复原生函数入口，运行中的游戏立即变色" : nativePatch.error());
    }

    private void showPatchDetails() {
        JTextArea details = new JTextArea(nativePatch.describe());
        details.setEditable(false);
        details.setFont(new Font(FONT, Font.PLAIN, 13));
        details.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(details);
        scroll.setPreferredSize(new Dimension(740, 390));
        JOptionPane.showMessageDialog(this, scroll, "原生函数入口 · 实时机器码", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshMusicPlayback() {
        music.setPlaying(windowFocused && game.state() != GameModel.State.PAUSED);
        refreshMusicButton();
    }

    private void refreshMusicButton() {
        boolean unavailable = music.status() == BackgroundMusic.Status.UNAVAILABLE;
        musicToggle.setEnabled(!unavailable && music.status() != BackgroundMusic.Status.CLOSED);
        musicToggle.setText(unavailable ? "音乐不可用" : music.isEnabled() ? "音乐：开" : "音乐：关");
        musicToggle.setToolTipText(unavailable ? "音频或输出设备暂不可用，游戏仍可正常游玩"
                : "A Walk in the Woods · 按 M 开关，暂停或切换窗口时暂停播放");
    }

    private void refresh() {
        scoreValue.setText(String.format("%03d", game.score()));
        difficultyValue.setText(session.difficulty().title);
        speed.setEnabled(!session.roundInProgress());
        speed.setToolTipText(session.roundInProgress() ? "本局难度已固定，结束后可切换" : "选择下一局的难度");
        for (Difficulty difficulty : Difficulty.values()) {
            List<Integer> top = scores.top(difficulty);
            rankTitles[difficulty.ordinal()].setText(difficulty.title + " · 前三名"
                    + (difficulty == session.difficulty() ? "（当前）" : ""));
            for (int rank = 0; rank < 3; rank++) {
                JLabel value = rankValues[difficulty.ordinal()][rank];
                value.setText(rank < top.size() ? top.get(rank) + " 分" : "—");
                value.setForeground(rank < top.size() ? (rank == 0 ? MINT : TEXT) : MUTED);
            }
        }
        play.setText(switch (game.state()) {
            case READY -> "开始游戏";
            case RUNNING -> "暂停";
            case PAUSED -> "继续游戏";
            case GAME_OVER, WON -> "再来一局";
        });
        status.setText(session.saveFailed() ? "成绩暂时无法保存，本局仍可正常游玩。" : switch (game.state()) {
            case READY -> "30 × 20 棋盘  ·  每颗食物 +10 分  ·  吃得越多，速度越快";
            case RUNNING -> "本局难度已固定  ·  结束时记录成绩，每种难度各保留前三名";
            case PAUSED -> "已暂停  ·  切换窗口时会自动暂停，返回后按空格继续";
            case GAME_OVER -> "本局已结算  ·  可切换难度，再按 Enter 或 R 开始";
            case WON -> "你填满了整个棋盘！按 Enter 开启下一局。";
        });
        Difficulty difficulty = session.difficulty();
        int delay = Math.max(55, difficulty.delay - game.score() / 10 * 3);
        timer.setDelay(delay);
        timer.setInitialDelay(delay);
        if (game.state() == GameModel.State.RUNNING) {
            if (!timer.isRunning()) timer.start();
        } else {
            timer.stop();
        }
        board.repaint();
        refreshNativePatch();
        refreshMusicPlayback();
    }

    private static Path findHome() {
        String configured = System.getProperty("snake.home");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath();
        try {
            Path source = Path.of(SnakeGame.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(source)) return source.getParent();
        } catch (Exception ignored) { /* 在 IDE 中使用项目工作目录。 */ }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    private final class Board extends JPanel {
        Board() {
            setOpaque(false);
            setPreferredSize(new Dimension(768, 520));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                smooth(g);
                g.setColor(SURFACE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                double cell = Math.min((getWidth() - 36.0) / game.columns(),
                        (getHeight() - 36.0) / game.rows());
                double width = cell * game.columns();
                double height = cell * game.rows();
                double x = (getWidth() - width) / 2;
                double y = (getHeight() - height) / 2;
                Shape bounds = new RoundRectangle2D.Double(x, y, width, height, 12, 12);
                g.setPaint(new GradientPaint((float) x, (float) y, new Color(11, 23, 33),
                        (float) (x + width), (float) (y + height), new Color(15, 31, 41)));
                g.fill(bounds);
                g.setClip(bounds);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(255, 255, 255, 7));
                for (int col = 1; col < game.columns(); col++) {
                    int gx = (int) Math.round(x + col * cell);
                    g.drawLine(gx, (int) y, gx, (int) (y + height));
                }
                for (int row = 1; row < game.rows(); row++) {
                    int gy = (int) Math.round(y + row * cell);
                    g.drawLine((int) x, gy, (int) (x + width), gy);
                }
                drawFood(g, x, y, cell);
                drawSnake(g, x, y, cell);
                if (game.state() != GameModel.State.RUNNING) drawOverlay(g, x, y, width, height);
                g.setClip(null);
                g.setColor(new Color(70, 96, 112, 100));
                g.draw(bounds);
            } finally {
                g.dispose();
            }
        }

        private void drawFood(Graphics2D g, double x, double y, double cell) {
            if (game.food() == null) return;
            double cx = x + (game.food().x() + .5) * cell;
            double cy = y + (game.food().y() + .5) * cell;
            for (int layer = 3; layer >= 1; layer--) {
                double size = cell * (.7 + layer * .22);
                g.setColor(new Color(255, 121, 117, 12));
                g.fillOval((int) (cx - size / 2), (int) (cy - size / 2), (int) size, (int) size);
            }
            int size = (int) (cell * .63);
            g.setColor(CORAL);
            g.fillOval((int) (cx - size / 2.0), (int) (cy - size / 2.0), size, size);
            g.setColor(new Color(255, 225, 210));
            g.fillOval((int) (cx - cell * .15), (int) (cy - cell * .17), 4, 4);
        }

        private void drawSnake(Graphics2D g, double x, double y, double cell) {
            List<GameModel.Cell> body = game.snake();
            Color nativeColor = new Color(nativePatch.colorRgb());
            for (int index = body.size() - 1; index >= 0; index--) {
                GameModel.Cell part = body.get(index);
                double progress = 1.0 - (double) index / body.size();
                double brightness = .50 + .40 * progress;
                g.setColor(index == 0 ? nativeColor : new Color((int) (nativeColor.getRed() * brightness),
                        (int) (nativeColor.getGreen() * brightness), (int) (nativeColor.getBlue() * brightness)));
                g.fill(new RoundRectangle2D.Double(x + part.x() * cell + 1.5,
                        y + part.y() * cell + 1.5, cell - 3, cell - 3, 7, 7));
            }
            GameModel.Cell head = body.getFirst();
            double cx = x + (head.x() + .5) * cell;
            double cy = y + (head.y() + .5) * cell;
            GameModel.Direction d = game.direction();
            for (int side : new int[]{-1, 1}) {
                double ex = cx + d.dx * cell * .19 + (d.dx == 0 ? side * cell * .20 : 0);
                double ey = cy + d.dy * cell * .19 + (d.dy == 0 ? side * cell * .20 : 0);
                g.setColor(new Color(246, 255, 250));
                g.fillOval((int) (ex - 3), (int) (ey - 3), 6, 6);
                g.setColor(new Color(12, 44, 37));
                g.fillOval((int) (ex - 1.5 + d.dx), (int) (ey - 1.5 + d.dy), 3, 3);
            }
        }

        private void drawOverlay(Graphics2D g, double x, double y, double width, double height) {
            g.setColor(new Color(6, 12, 22, 180));
            g.fill(new RoundRectangle2D.Double(x, y, width, height, 12, 12));
            int boxWidth = (int) Math.min(410, width - 30);
            int boxHeight = 196;
            int left = (int) (x + (width - boxWidth) / 2);
            int top = (int) (y + (height - boxHeight) / 2);
            g.setColor(new Color(22, 35, 50, 245));
            g.fillRoundRect(left, top, boxWidth, boxHeight, 22, 22);
            g.setColor(new Color(77, 111, 126));
            g.drawRoundRect(left, top, boxWidth, boxHeight, 22, 22);
            String tag;
            String title;
            String hint;
            switch (game.state()) {
                case READY -> { tag = "LET'S PLAY"; title = "准备好了吗？"; hint = "按 Enter 开始，或点击下方按钮"; }
                case PAUSED -> { tag = "TAKE A BREATH"; title = "休息一下"; hint = "按空格继续游戏"; }
                case WON -> { tag = "PERFECT RUN"; title = "完美通关！"; hint = "你填满了整个棋盘，按 Enter 再挑战"; }
                default -> { tag = "ONE MORE TRY"; title = "游戏结束"; hint = "本局 " + game.score() + " 分 · 按 Enter 再来一局"; }
            }
            int center = left + boxWidth / 2;
            centered(g, tag, center, top + 39, 11, MINT, Font.BOLD);
            centered(g, title, center, top + 87, 29, TEXT, Font.BOLD);
            centered(g, hint, center, top + 123, 13, MUTED, Font.PLAIN);
            centered(g, "移动  ↑ ↓ ← →  /  W A S D", center, top + 159, 12, MUTED, Font.PLAIN);
        }
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(FONT, Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private static JLabel valueLabel() {
        JLabel label = label("000", 27, TEXT);
        label.setFont(new Font("Consolas", Font.BOLD, 27));
        return label;
    }

    private static void centered(Graphics2D g, String text, int x, int baseline,
            int size, Color color, int style) {
        g.setFont(new Font(FONT, style, size));
        g.setColor(color);
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    private static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static final class RoundedPanel extends JPanel {
        RoundedPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            g.setColor(SURFACE);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ArcadeButton extends JButton {
        private final boolean primary;
        ArcadeButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setUI(new BasicButtonUI());
            setFont(new Font(FONT, Font.BOLD, 12));
            setForeground(primary ? new Color(9, 39, 31) : TEXT);
            setBorder(BorderFactory.createEmptyBorder(10, 19, 10, 19));
            setContentAreaFilled(false);
            setFocusable(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            Color color = primary ? MINT : new Color(34, 48, 67);
            g.setColor(getModel().isRollover() ? color.brighter() : color);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
