package snake;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests the actual F8 action, native-dependent rendering and session state. */
public final class NativePatchUiTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path home = (args.length > 0 ? Path.of(args[0]) : Path.of(".")).toAbsolutePath();
        Path images = Path.of("out", "native-ui").toAbsolutePath();
        Files.createDirectories(images);
        SwingUtilities.invokeAndWait(() -> {
            SnakeGame panel = null;
            try {
                var constructor = SnakeGame.class.getDeclaredConstructor(Path.class);
                constructor.setAccessible(true);
                panel = constructor.newInstance(home);
                NativeColorPatch patch = field(panel, "nativePatch", NativeColorPatch.class);
                if (!patch.available()) {
                    System.out.println("SKIP: native UI checks (DLL not built)");
                    return;
                }
                patch.setApplied(false);
                panel.setSize(panel.getPreferredSize());
                layout(panel);
                GameModel game = field(panel, "game", GameModel.class);
                panel.getActionMap().get("ENTER").actionPerformed(new ActionEvent(panel, 0, "ENTER"));
                field(panel, "timer", Timer.class).stop();
                game.tick();
                var body = game.snake();
                var food = game.food();
                int score = game.score();
                long pid = patch.snapshot().processId();
                JButton button = field(panel, "patchToggle", JButton.class);
                JPanel board = field(panel, "board", JPanel.class);
                check(headPixel(board, game) == NativeColorPatch.ORIGINAL_RGB, "renderer initially uses native mint");
                render(panel, images.resolve("original.png"));
                panel.getActionMap().get("F8").actionPerformed(new ActionEvent(panel, 0, "F8"));
                check(patch.snapshot().applied(), "F8 applies actual native patch");
                check(headPixel(board, game) == NativeColorPatch.PATCHED_RGB, "renderer uses native purple result");
                check(button.getText().contains("恢复"), "button offers restoration");
                check(game.state() == GameModel.State.RUNNING && game.snake().equals(body)
                        && game.food().equals(food) && game.score() == score
                        && field(panel, "session", GameSession.class).roundInProgress(),
                        "patch preserves the running round");
                render(panel, images.resolve("patched.png"));
                button.doClick();
                check(!patch.snapshot().applied() && headPixel(board, game) == NativeColorPatch.ORIGINAL_RGB,
                        "button restores machine code and rendered color");
                check(patch.snapshot().processId() == pid && button.getText().contains("应用"),
                        "same process continues after restoration");
                render(panel, images.resolve("restored.png"));
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                if (panel != null) {
                    try {
                        field(panel, "nativePatch", NativeColorPatch.class).setApplied(false);
                        field(panel, "music", BackgroundMusic.class).close();
                        field(panel, "timer", Timer.class).stop();
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }
            }
        });
        System.out.println("PASS: " + checks + " native UI checks");
    }

    private static int headPixel(JPanel board, GameModel game) {
        BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { board.paint(graphics); } finally { graphics.dispose(); }
        double cell = Math.min((board.getWidth() - 36.0) / game.columns(), (board.getHeight() - 36.0) / game.rows());
        int x = (int) Math.round((board.getWidth() - cell * game.columns()) / 2 + (game.snake().getFirst().x() + .5) * cell);
        int y = (int) Math.round((board.getHeight() - cell * game.rows()) / 2 + (game.snake().getFirst().y() + .5) * cell);
        return image.getRGB(x, y) & 0xffffff;
    }

    private static void render(JPanel panel, Path output) throws Exception {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", output.toFile());
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(object));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
