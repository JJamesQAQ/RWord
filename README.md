# RWord

在 JetBrains Rider 状态栏学习英语的轻量插件，支持自定义中英词表和句子、离线发音、随机浏览和已记住单词管理。

最左侧是 `Word` / `Sentence`，用来切换单词和句子。单词模式下：Auto、上一个、当前词、下一个、`EN+`、`Got`、进度、`Copy`。句子模式隐藏 `Got`，最右侧 `Copy` 复制当前英文；句子超出宽度时，拖动或滚轮横向滑动。单词模式点击英文发音；句子模式悬停英文单词会高亮，点击只复制该单词（不含两侧标点，保留词内撇号和连字符）。点击中文、英文空格或句子区域空白处朗读整句；拖动不会触发复制或朗读。`Got` 经确认后标记已记住，返回上一个后可用 `Undo` 恢复。

## 使用

Settings → RWord：

- 词表每行一个词，`英文<Tab>中文` 或 `英文 | 中文`
- 浏览方式可选“按词表顺序”或“随机顺序”（只作用于词表）
- 句子：直接粘贴英文和中文，点击“确认并识别”后检查结果。编辑器会根据英文/中文切换自动分组，不要求每组之间一定有空行；识别结果可逐组编辑、删除，或在某组下方增加空组。句子按粘贴顺序浏览，不随机，也不能标记已记住

空行在词表里会被忽略；句子会在英文/中文切换处自动分组，空行只是可选分隔。确认后的句对可以保留空白或只有一侧的内容，由用户自行编辑；状态栏只浏览完整的中英句对。重复的英文（忽略大小写）保留第一次出现的写法。
当前词、当前句和随机顺序会记住，重新打开 Rider 后从上次的位置继续。

发音使用 Windows 自带的英文语音，不联网。切换内容会打断当前读音；再次点击正在读的内容会停止。其他系统上发音会提示不可用，浏览不受影响。

## 界面

设置页维护词表、导入文件，并查看已记住的词：

![设置页](rword-settings.png)

状态栏从左到右是 `Word`/`Sentence`、自动朗读、上一个、当前内容、下一个、中英显示、记住（仅单词）、进度和最右侧的 `Copy`：

![状态栏](rword-status-bar.png)

## 构建

需要 Rider 2026.2 自带的 JBR（Java 25），不需要 Gradle。

```bash
RIDER="C:/Program Files/JetBrains/JetBrains Rider 2026.2.0.2"
CP=$(find "$RIDER/lib" -name "*.jar" | tr '\n' ';')
CLASSPATH="$CP" "$RIDER/jbr/bin/javac.exe" --release 25 -encoding UTF-8 -d classes $(find src -name "*.java")
```

然后把 `classes` 和 `src/META-INF/plugin.xml` 打成
`RWord/lib/rider-word.jar`，再整体压成 zip。成品在 `dist/RWord.zip`。

自检：

```bash
python build/run-tooltip-check.py
```

## 安装

Settings → Plugins → 齿轮 → Install Plugin from Disk，选择 `dist/RWord-0.2.0-signed.zip`，重启 Rider。
仅适用于 Rider 2026.2。
