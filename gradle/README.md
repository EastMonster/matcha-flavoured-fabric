# JAR 中的 PNG 优化

`jar`（包括 `build` / `assemble` 调用的打包任务）使用 oxipng + Zopfli 优化 PNG。
`runClient` 不依赖此优化任务；`sourcesJar` 保留原始资源。

- 输入为 `processResources` 的结果，包含构建生成的纹理。
- 仅将 PNG 复制到 `build/optimizedJarPngs/` 后优化；源资源和普通资源处理结果不变。
- 参数为 `-q -o max --fast --ng -z --zi 15 -t 4`，不启用有损选项。安静模式关闭 oxipng 的逐文件输出；缺少工具时的警告和非零退出码导致的构建错误仍保留。
- 输入、优化器二进制和参数未变时跳过任务；只改动 PNG 时仅重新压缩对应文件，删除的 PNG 同步从副本移除。
- 已启用 Gradle 本地构建缓存。输出可从缓存恢复；`clean` 会清除构建目录，但不清除本地构建缓存。
- 从 `PATH` 查找 `oxipng`（Windows 为 `oxipng.exe`）。找不到时打印警告，使用未优化的 PNG 副本继续构建；找到但执行失败时仍报错。
- 工具的存在状态和二进制内容均参与输入快照：安装、移除或更换工具后重新生成副本，避免沿用另一种状态的缓存。

本机需将 oxipng 所在目录加入 `PATH`；不再使用工作区内的固定路径或 `oxipngExecutable` 属性。
工具不随 Mod 分发。日常 `runClient` 不要求安装此工具。

GitHub Actions 的 Ubuntu x64 发布构建固定下载官方 oxipng **10.2.1**，
校验归档的 SHA-256 后再解压，并通过 `GITHUB_PATH` 提供给 Gradle。
下载、校验或安装失败会阻止 CI 发布，避免发布构建意外跳过优化。
版本及校验值位于 `.github/workflows/build.yml`，升级时需要一起更新。
校验值来源：<https://github.com/oxipng/oxipng/releases/expanded_assets/v10.2.1>。

首次优化或优化器/参数变化后，需要重新优化全部 PNG；Zopfli 可能耗时数分钟。
