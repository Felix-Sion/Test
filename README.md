# Test · 贪吃蛇 Snake Arcade

使用 **Java 21 + Swing** 编写的桌面小游戏，无第三方依赖。

## 从 GitHub 构建

仓库包含源码、测试和构建脚本；编译产物、Java 运行环境及个人存档不提交到 Git。

1. 安装 JDK 21 或更高版本，并将 `JAVA_HOME` 指向 JDK 安装目录。
2. 克隆仓库后进入项目目录：

```text
git clone https://github.com/Felix-Sion/Test.git
cd Test
build.cmd
run.cmd
```

`build.cmd` 会编译源码、执行测试并生成 `SnakeGame.jar`。如需 Windows EXE 免安装版，运行 `build-exe.cmd`，生成的游戏和完整 ZIP 位于 `dist` 目录。

## 启动

- **Windows EXE 免安装版**：双击 `dist/SnakeGame/SnakeGame.exe`，无需额外安装 Java。
- 分享时发送 `dist/SnakeGame-Windows-x64.zip`；完整解压后运行其中的 EXE。
- EXE 必须与同目录下的 `app`、`runtime` 文件夹一起保留。
- 双击项目中的 **run.cmd**，直接启动打包好的游戏。
- 或者用 IntelliJ IDEA 打开项目文件夹，选择 JDK 21，再选择 **SnakeGame** 运行配置，点击绿色运行按钮。
- 修改代码后，双击 **build.cmd** 重新编译、测试并打包。
- 双击 **build-exe.cmd** 可重新编译、测试并生成带 Java 运行环境的 Windows EXE 与 ZIP。已有发布目录会保留，新版本另存到 `dist/build-编号`，避免覆盖存档。

## 操作

| 按键 | 功能 |
| --- | --- |
| 方向键 / W A S D | 控制方向；待开始时也可直接开始 |
| Enter / 空格 | 开始、暂停、继续；游戏结束后再来一局 |
| R | 立即重新开始 |
| M | 开启 / 关闭背景音乐 |

吃到一颗食物加 10 分、蛇身增长一格，速度会逐步提高。撞墙或撞到自己的身体，本局结束。蛇不能直接掉头。填满棋盘即通关。

界面可以选择悠闲、标准、挑战三种难度。每局开始后难度固定，暂停时也不能变更；本局结束后可以选择下一局的难度。切换到其他窗口时会自动暂停，回来后按空格继续。

背景音乐使用项目中的 **A Walk in the Woods.mp3**，窗口打开后循环播放。可以点击“音乐：开 / 关”按钮或按 **M** 键切换。暂停游戏、切换窗口时音乐暂停，继续游戏时从原位置恢复；关闭游戏时释放音频设备。音频载入在后台进行，音频设备不可用时游戏仍可正常运行。

音乐已转换为 Java 原生支持的 PCM 音频，放在 `resources/audio/background.wav` 并内置于 JAR 和 EXE，无需第三方 Java 音频库。原始 MP3 留在本地。

界面同时显示三种难度各自的前三名，按得分从高到低排列。游戏结束、按 R 重开或关闭窗口时登记本局成绩，每局只登记一次。不同对局的同分成绩可以分别占位，0 分不入榜。

排行榜保存在本项目的 `data/leaderboards.properties`，重新启动后仍会保留，不向 C 盘写入游戏存档。EXE 版使用 EXE 目录下的 `data` 文件夹。旧版 `best-score.txt` 由于没有注明难度，保留原文件并显示为历史综合纪录，不混入新榜单。

## 项目结构

- `src/snake/SnakeGame.java`：程序入口、窗口、键盘操作、绘图和排行榜显示。
- `src/snake/Difficulty.java`：三种难度及对应速度。
- `src/snake/GameSession.java`：每局结算、难度固定与防止重复登记。
- `src/snake/ScoreBoard.java`：三组前三名的保存与读取。
- `src/snake/BackgroundMusic.java`：异步载入、循环播放、暂停和静音。
- `resources/audio/background.wav`：内置背景音乐。
- `src/snake/GameModel.java`：移动、食物、计分、碰撞等游戏规则。
- `tests/snake/GameModelTest.java`：无需额外框架的游戏规则测试。
- `tests/snake/ScoreBoardTest.java`：分难度排名、存档、对局结算等测试。
- `tests/snake/BackgroundMusicTest.java`：音频资源、循环、静音、异步关闭和设备异常测试。
- `SnakeGame.jar`：可运行的游戏包。
- `build.cmd` / `run.cmd`：Windows 编译与启动脚本。
- `build-exe.cmd`：生成 Windows x64 EXE 免安装版与完整 ZIP。
- `dist/SnakeGame`：包含 Java 运行环境的可执行游戏目录，存档放在此目录的 `data` 文件夹。

## 命令行启动

在项目目录执行：

```text
java -jar SnakeGame.jar
```

程序会把存档放在 JAR 所在目录。IDEA 运行配置也已指向本项目目录。
