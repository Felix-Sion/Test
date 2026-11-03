# Test · 贪吃蛇 Snake Arcade

使用 **Java 21 + Swing** 编写的桌面小游戏，无第三方 Java 依赖。Windows x64 版包含一个 JNI 原生 DLL，用于演示运行时修改函数入口机器码、应用和恢复颜色补丁。

## 从 GitHub 构建

仓库包含源码、测试和构建脚本；编译产物、Java 运行环境及个人存档不提交到 Git。

1. 安装 JDK 21 或更高版本，并将 `JAVA_HOME` 指向 JDK 安装目录。完整 Windows x64 构建还需安装 [Zig](https://ziglang.org/download/)（本机使用 0.17.0），将 `ZIG_EXE` 指向 `zig.exe` 的完整路径。
2. 克隆仓库后进入项目目录：

```text
git clone https://github.com/Felix-Sion/Test.git
cd Test
build.cmd
run.cmd
```

`build.cmd` 会编译原生 DLL 和 Java 源码、执行测试并生成 `SnakeGame.jar`。如需 Windows EXE 免安装版，运行 `build-exe.cmd`，生成的游戏和完整 ZIP 位于 `dist` 目录。没有 C 编译器时，可运行 `build.cmd --java-only` 只构建 Java 游戏；缺少 DLL 时颜色补丁不可用，其他游戏功能正常。

## 启动

- **Windows EXE 免安装版**：双击 `dist/SnakeGame/SnakeGame.exe`，无需额外安装 Java。
- 分享时发送 `dist/SnakeGame-Windows-x64.zip`；完整解压后运行其中的 EXE。
- EXE 必须与同目录下的 `app`、`runtime`、`native` 文件夹一起保留。
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
| F8 | 应用 / 恢复原生颜色热补丁，薄荷绿与紫色切换 |

吃到一颗食物加 10 分、蛇身增长一格，速度会逐步提高。撞墙或撞到自己的身体，本局结束。蛇不能直接掉头。填满棋盘即通关。

## 原生机器码热补丁

运行游戏时按 **F8** 或点击 **应用补丁 [F8]**，蛇立即变成紫色；再次按 F8 或点击 **恢复原始 [F8]**，恢复薄荷绿。切换不会重开游戏，不修改得分规则、难度或排行榜。补丁只影响当前进程，下次启动恢复绿色。

点击 **查看机器码** 可以查看当前进程 PID、原函数地址、补丁函数地址、代码页权限和实时入口字节。修改前入口为 `F3 0F 1E FA` 和 16 个 `90`；修改后保留前 4 字节，后面写入 `FF 25 00 00 00 00`、8 字节实际目标地址及 2 个 `90`。

游戏通过 JNI 调用 `snakepatch.dll` 中的原生取色函数。F8 使用 `VirtualProtect` 修改该函数预留区域，写入无条件 `JMP`，调用 `FlushInstructionCache` 并恢复页面权限；取色调用与写入通过同一把原生读写锁同步。此处确实修改了 DLL 内存中的机器码，Java 的绘图逻辑一直调用同一个原生函数入口。

这是针对自有 DLL 中已知、预留了 NOP 空间的函数制作的演示，完整替换函数返回值，不需要 trampoline；它不提供任意函数的指令解码和重定位。Java 方法及 JVM/JIT 代码没有被改写。详见 [原生实现与验证说明](native/README.md)。

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
- `src/snake/NativeColorPatch.java`：加载原生 DLL、补丁控制和机器码快照。
- `native/snakepatch.c`：同进程入口写入、线程同步、页面保护和缓存刷新。
- `native/color_x64.S`：带预留 NOP 区域的原函数和紫色补丁函数。
- `resources/audio/background.wav`：内置背景音乐。
- `src/snake/GameModel.java`：移动、食物、计分、碰撞等游戏规则。
- `tests/snake/GameModelTest.java`：无需额外框架的游戏规则测试。
- `tests/snake/ScoreBoardTest.java`：分难度排名、存档、对局结算等测试。
- `tests/snake/BackgroundMusicTest.java`：音频资源、循环、静音、异步关闭和设备异常测试。
- `tests/snake/NativeColorPatchTest.java`：真实机器码、跳转目标、恢复、页面权限及并发压力测试。
- `tests/snake/NativePatchUiTest.java`：F8、恢复按钮、原生返回值绘制及保留本局状态测试。
- `SnakeGame.jar`：可运行的游戏包。
- `build.cmd` / `run.cmd`：Windows 编译与启动脚本。
- `build-exe.cmd`：生成 Windows x64 EXE 免安装版与完整 ZIP。
- `build-native.cmd`：使用 Zig C 编译器生成原生 DLL，编译器缓存和临时文件都在项目 `out` 目录。
- `dist/SnakeGame`：包含 Java 运行环境的可执行游戏目录，存档放在此目录的 `data` 文件夹。

## 命令行启动

在项目目录执行：

```text
java -jar SnakeGame.jar
```

程序会把存档放在 JAR 所在目录。IDEA 运行配置也已指向本项目目录。

通过 JAR 或 IDEA 使用原生演示时，保留项目下的 `native/windows-x64/snakepatch.dll`。DLL 不内嵌到 JAR，单独复制 JAR 时游戏仍可运行，但原生颜色补丁会被禁用。
