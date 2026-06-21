# macOS / Linux 开发环境

> 跟 Windows 一样：只需要 `mvn` 命令能跑起来。
> 本项目没有自带 Maven Wrapper (`mvnw`)，所以你要么把 `mvn` 放进 `PATH`，
> 要么用 IDE 内置的 Maven（IntelliJ 自带）。

## 怎么知道环境就绪

```bash
mvn -version
```

期望看到：

```
Apache Maven 3.9.x
Java version: 21.x
```

如果提示 `mvn: command not found`：

- **方法 1（推荐 macOS）**：`brew install maven`（用 Homebrew）
- **方法 2（Linux apt）**：`sudo apt install openjdk-21-jdk maven`
- **方法 3（macOS MacPorts）**：`sudo port install maven`
- **方法 4**：手动下载 Maven 二进制，`<maven>/bin` 加到 `PATH`
- **方法 5**：用 IDE 自带 Maven（IntelliJ → Settings → Build Tools → Maven，勾 "Bundled Maven"）

如果 `java -version` 仍是 1.8 / 17：

- 项目要求 JDK 21。
- macOS：`brew install --cask temurin@21`，然后 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`
- Linux：`sudo apt install openjdk-21-jdk`，然后 `export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::")`

## 跑测试 / 构建

环境就绪后，所有平台都一样：

```bash
mvn test                       # 跑单元测试
mvn -q -DskipTests package     # 打 jar
```

## IDE 配置（IntelliJ IDEA）

- `Settings → Build, Execution, Deployment → Build Tools → Maven`
- "Maven home path" 选 "Bundled (Maven 3)" 或指向你系统装的 Maven
- "Project SDK" 选 21
- macOS 改完保存，IDE 自己会用对的 Maven 跑测试

## IDE 配置（VS Code）

- 装扩展 `Extension Pack for Java`
- `settings.json` 里加：
  ```json
  "java.configuration.runtimes": [
    { "name": "JavaSE-21", "path": "<JDK 21 安装路径>" }
  ]
  ```
  > macOS 用 `$(/usr/libexec/java_home -v 21)` 取路径；Linux 用 `readlink -f $(which java) | sed 's:bin/java::'`

## 平台无关提示

- `mvn test` 在 macOS / Linux / Windows 行为一致。
- Playwright 启动时 `java -jar target/szu-agent-plugin.jar ...` 跨平台相同。
- 路径一律写 `/`（Java 21 + Maven 自动处理跨平台）。
