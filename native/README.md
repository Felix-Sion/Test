# 原生函数入口热补丁演示（Windows x64）

这个演示为 Java 贪吃蛇增加了一个自有 PE DLL。JNI 包装函数每次都调用相同的 `snake_color_target`，根据实际执行结果绘制蛇的颜色。F8 只改该函数入口的机器码，不用 Java 布尔变量选择另一种颜色。

## 使用

运行 `dist/SnakeGame/SnakeGame.exe`，按 F8 应用补丁使蛇变紫，再按 F8 恢复绿色。在准备、运行、暂停及结束状态都能切换，游戏进程和本局数据保持不变。点击“查看机器码”可查看实时字节和进程信息。

补丁函数与原函数都是 `int function(void)`，遵守 Windows x64 ABI，返回 `0x63EDB4`（薄荷绿）或 `0xBB86FC`（紫色）。颜色以外的规则保持不变。

## 原函数的机器码

`color_x64.S` 明确定义了入口，避免编译优化内联或改变函数序言：

```text
原函数入口 + 0   F3 0F 1E FA       ENDBR64，保留为间接分支落点
原函数入口 + 4   90 × 16           预留补丁区域
原函数入口 + 20  B8 B4 ED 63 00    MOV EAX, 0x63EDB4
原函数入口 + 25  C3                RET
```

应用补丁后，入口 +4 的 16 字节变为：

```text
FF 25 00 00 00 00     JMP QWORD PTR [RIP+0]
xx xx xx xx xx xx xx xx  补丁函数真实地址，小端序
90 90                 剩余 NOP
```

这是一条通过内存间接寻址的无条件跳转，后续地址是数据，CPU 不会顺序执行它。ASLR 之后用函数指针取得实际地址，因此没有硬编码 DLL 加载基址。恢复时将入口 +4 的全部 16 字节写回原始值。

## 写入过程和线程同步

1. 所有 JNI 取色调用取得同一把 SRW 读锁，在原生函数返回后释放。
2. 写入取得 SRW 写锁，等待已有取色调用结束，阻止新的取色调用进入。
3. 确认目标仍是已知的原始字节或本演示写入的补丁；不覆盖未知入口。
4. `VirtualProtect` 临时设置 `PAGE_EXECUTE_READWRITE`，写入完整补丁或原始字节。
5. `FlushInstructionCache` 刷新代码区域，随后恢复原来的页面权限并释放写锁。

该锁的保证成立，是因为此演示中所有目标函数调用都经过受控 JNI 包装。不能把同样的锁直接套用到不配合该协议的其他函数或第三方程序。

原入口的前 4 字节保留，补丁函数也以 ENDBR64 开头。两个原生函数都是无栈帧的叶函数；完整替换后直接返回原调用者，不调用被覆盖的原实现，因此无需 trampoline。

## 构建

需要 JDK 21 和可编译 Windows x64 C/汇编的 Zig（已用 0.17.0 验证）。例如在命令提示符中：

```bat
set "JAVA_HOME=E:\DevTools\Java\jdk-21.0.13+9"
set "ZIG_EXE=E:\DevTools\Zig\zig-x86_64-windows-0.17.0\zig.exe"
build.cmd
build-exe.cmd --skip-build
```

`build-native.cmd` 单独编译 DLL。缓存及临时文件位于 `out/zig-cache` 和 `out/native`；生成的 DLL 位于 `native/windows-x64/snakepatch.dll`，不会提交到 Git。打包脚本把它复制到 EXE 同目录下的 `native/windows-x64`。接收免安装版的用户无需安装 Zig 或 JDK。

如果从当前项目目录启动的游戏仍在加载 DLL，Windows 可能不允许覆盖文件；关闭该游戏窗口后再构建。缺少 DLL 或平台不是 Windows x64 时，游戏保留绿色并提供加载失败原因，其他功能仍可使用。

## 验证

完整 `build.cmd` 执行现有游戏、排行榜和音乐测试，并增加：

- 验证实际进程 PID、原函数地址在应用和恢复时保持一致。
- 验证入口从 NOP 变成 `JMP [RIP+0]`，并验证嵌入的 8 字节地址等于补丁函数地址。
- 真实调用同一个原生入口，确认返回值从绿色变紫色再恢复。
- 验证完整原始入口字节、只读可执行页面权限及磁盘 DLL 的 SHA-256 保持不变。
- 4 个读线程共执行 100,000 次取色，同时执行 2,000 次应用和恢复。
- 通过实际 Swing F8 动作和恢复按钮操作，验证绘图像素随原生返回值变化，本局蛇身、食物、分数及状态保持不变；预览图生成在 `out/native-ui`。

该实现用于演示已知入口的函数替换。通用的原生函数拦截还需要处理任意指令边界、相对地址重定位、跳板以及不同的线程执行状态；这里没有把演示包装成通用拦截库。

参考：[VirtualProtect](https://learn.microsoft.com/windows/win32/api/memoryapi/nf-memoryapi-virtualprotect)、[FlushInstructionCache](https://learn.microsoft.com/windows/win32/api/processthreadsapi/nf-processthreadsapi-flushinstructioncache)、[SRW 锁](https://learn.microsoft.com/windows/win32/sync/slim-reader-writer--srw--locks)、[Microsoft Detours 原理](https://github.com/microsoft/Detours/wiki/OverviewInterception)。
