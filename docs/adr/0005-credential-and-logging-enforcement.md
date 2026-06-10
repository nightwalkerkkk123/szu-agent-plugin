# ADR-0005 · 凭证流转与日志脱敏的强制约束

**Date:** 2026-06-11
**Status:** Accepted
**Supersedes:** 无
**Extends:** ADR-0001 D6(凭证分层)、D8(提交物)、CLAUDE.md 安全约束

---

## Context

ADR-0001 D6 把凭证读法定为三层:进程 env → cwd `.env` → Skill 注入。但具体怎么让 Skill wrapper 把自家目录的 `.env` 告诉 CLI,有两种走法:
- (A) Skill wrapper `cd $SKILL_DIR && java -jar ...`,让 jar 自己找 cwd `.env`
- (B) Skill wrapper 显式传 `--env-file <path>` 参数给 jar

CLAUDE.md 强制"敏感信息不写日志,`LogMasker` 集中脱敏"。但 SLF4J 没法在 logger 内部脱敏(看 ADR-0001 §3.2 已知坑),只能约束调用方。两种约束方式:
- (X) Code review 人肉检查
- (Y) archunit 静态规则禁止危险 pattern

---

## Decisions

### D1 凭证路径显式传参

Skill wrapper 启动 CLI 时,必须传 `--env-file <path>` 参数。jar 不依赖 cwd。

**理由**:
- ✅ 可测:测试时 `java -jar ... --env-file src/test/resources/.env.test`,不污染 cwd
- ✅ 可调:Agent 调 jar 时可以用任意绝对路径
- ✅ 可控:即使 jar 启动后 `System.setProperty("user.dir")` 被改,凭证路径不变
- ❌ 排他:如果用户直接 `java -jar` 不传参数,行为是"找不到 .env 但仍能从进程 env 读到",不致命

**实现**:Bootstrap 启动时读 `--env-file`,转交给 `AccountResolver` 构造器,resolver 内部包 `Dotenv` 实例。

**调用形态**:
```bash
# 直接调(进程 env 优先,无 .env 也行)
java -jar szu-agent-plugin.jar booking --username 2023150090 ...

# Skill wrapper 调
java -jar /opt/szu-agent-plugin.jar booking \
    --env-file /opt/skills/szu-sports/.env \
    --username 2023150090 ...

# 演示 fallback
java -jar szu-agent-plugin.jar booking \
    --env-file /home/wzh/demo/.env \
    --username 2023150090 ...
```

### D2 archunit 强制 LogMasker

**禁止**任何业务代码出现以下 pattern:
- `Logger.info|debug|warn|error` 的字符串字面量含 `password`、`pwd`、`secret`、`token`、`cookie`、`session`、`cookie`、`authorization`、`bearer`
- `System.out.println` / `System.err.println` / `printStackTrace` (除 `Main.main` 外)
- `System.getenv("SZU_PASSWORD")` 等直接读凭证(必须走 `AccountResolver`)

**理由**:
- ✅ 静态规则 0 成本,CI 跑过即生效
- ✅ 失败信息精准:哪一行、哪个 logger、哪段字符串命中
- ✅ 给老师看到是"工程化实践",加分
- ❌ 限制:无法检测"运行时拼接的字符串"(`log.info("login as " + user)`)→ 补救措施:约定所有 `log.info` 入参**先过** `LogMasker.scrub()`

**实现**:在 Phase 0 收尾时加 `com.tngtech.archunit:archunit-junit5` 依赖,写 `ArchitectureTest` 跑规则。

---

## Consequences

### 好处
- 凭证流转路径有 3 个独立可测点(进程 env / --env-file / Skill 注入),不耦合 cwd
- 演示翻车时可以"切演示脚本"立刻改凭证源,不用重启 wrapper
- 答辩时老师问"密码怎么不写日志" → "archunit 跑规则,grep 不到任何 password 字面量"
- 老师问"凭证怎么传给 jar" → 展示 `--env-file` 参数 + 三层查找代码,**不是 cwd 偶然**

### 代价 / 风险
- **archunit 多 2-3 秒测试时间**:可接受,CI 不会卡
- **架构测试** `ArchitectureTest` 写得不好会误报**:**Phase 0 收尾时**先**跑 1 个绿色用例再铺
- **`LogMasker.scrub(msg)`** 调用是约定不是强制:**补救**:在 README"开发约定"小节明文写出"任何 log.info 前必须 scrub"

---

## 实施细节

### D1 涉及改动
- `Main.java`: picocli 加 `@Option(names = "--env-file") String envFile`
- `Bootstrap.java`: 读 `envFile`,构造 `AccountResolver(envFile)`
- `AccountResolver.java`: 3 层查找(进程 env → envFile 指向的 Dotenv → 抛 MissingCredentialException)

### D2 涉及改动
- `pom.xml`: 加 `archunit-junit5` test 依赖
- `src/test/java/com/szu/architecture/LogMaskerRuleTest.java`: 3 个 `@ArchTest` 规则
- `src/test/java/com/szu/architecture/SystemOutRuleTest.java`: 1 个 `@ArchTest` 规则
- 这 2 个 test class 是 Phase 0 收尾的最后一步

---

## 引用

- ADR-0001 §D6(凭证分层)
- ADR-0001 §3.2 已知坑(SLF4J 没法在内部脱敏)
- CLAUDE.md 安全约束
- Java 21 + archunit 0.23.x 文档
