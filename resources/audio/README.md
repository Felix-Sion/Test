# 背景音乐

使用项目所有者提供的 `A Walk in the Woods.mp3`，曲名为 **A Walk in the Woods**，音频标签中的作者为 **Evan Call**。

`background.wav` 是该文件转换得到的 44.1 kHz、16 位、双声道 PCM 音频，供 Java Sound 直接播放。原始 MP3 保持在本地，不移动、不修改。

音乐随 JAR 和 EXE 内置，无需额外下载解码器。开发时 IntelliJ IDEA 会将 `resources` 目录作为资源目录。

如需重新生成，安装 FFmpeg 后在项目目录执行：

```text
ffmpeg -i "A Walk in the Woods.mp3" -map_metadata -1 -vn -ar 44100 -ac 2 -c:a pcm_s16le resources/audio/background.wav
```
