# Windows 开发环境

> 跟 macOS / Linux 一样：只需要 `mvn` 命令能跑起来。
> 本项目没有自带 Maven Wrapper (`mvnw`)，所以你要么把 `mvn` 放进 `PATH`，
> 要么用 `IDE 内置的 Maven`（IntelliJ 自带）。

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

- **方法 1（推荐）**：用包管理器装到 PATH。
  - `winget install Apache.Maven`
  - `choco install maven`
  - `scoop install maven`
- **方法 2**：把现有 Maven 二进制加到 PATH（`sysdm.cpl` → 高级 → 环境变量 → PATH 追加 `<maven>\bin`）。
- **方法 3**：用 IDE 自带 Maven（IntelliJ → Settings → Build Tools → Maven，勾上 "Bundled Maven"）。

如果 `java -version` 仍然是 1.8：

- 项目要求 JDK 21；用 `winget install EclipseAdoptium.Temurin.21.JDK`，
  并把 `JAVA_HOME` 指向新装的 JDK。

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
- 改完保存，IDE 自己会用对的 Maven 跑测试

## IDE 配置（VS Code）

- 装扩展 `Extension Pack for Java`
- `settings.json` 里加：
  ```json
  "java.configuration.runtimes": [
    { "name": "JavaSE-21", "path": "<JDK 21 安装路径>" }
  ]
  ```
  > 具体路径查 `mvn -version` 第一行（`Java home:` 字段），把那一行粘过来。

## 公司代理 / 镜像

如果内网有 Maven 镜像，建一个 `~/.m2/settings.xml`，把 `<mirror>` 配好。`mvn -s <path>` 也行。

## 常见错误

| 现象 | 原因 | 解决 |
|---|---|---|
| `mvn: command not found` | PATH 里没有 Maven | 见上文三种方法 |
| `java.lang.UnsupportedClassVersionError` | 默认 Java 仍是 1.8 | 装 JDK 21 并设置 `JAVA_HOME` |
| `Could not resolve dependencies` | 仓库被墙 / 没配镜像 | 配 `~/.m2/settings.xml` |
