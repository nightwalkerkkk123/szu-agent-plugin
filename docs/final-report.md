# 面向对象高级编程 期末报告

> **学号**: 2023150090
> **姓名**: 王子豪
> **题目**: SZU Agent Plugin — 面向 AI Agent 的深圳大学校园自动化插件
> **代码仓库**: https://github.com/nightwalkerkkk123/szu-agent-plugin
> **提交日期**: 2026-06-20

---

## 一、题目背景与动机

### 1.1 现状

SZU 内部网(ehall、CAS、企业微信、畅课)有大量重复固定操作。网球场预约：每天 20:00 放号，手慢即无。

### 1.2 痛点

AI Agent 不知 SZU 页面结构与登录流程，无法直接完成任务；学生不愿为多平台单独写代码；暴露浏览器自动化存在密码泄漏风险。

### 1.3 解决思路

校园流程封装为标准化工具：Java CLI 接收结构化参数，Playwright 本机执行(密码不离本机)，Skill/MCP 薄壳统一协议，重试策略保障。

### 1.4 课程要求对齐

4 设计模式、6 编程技术、80%+ 覆盖率、完整文档。

## 二、项目愿景:深大智能助手工具集

### §2.1 项目愿景

本项目将深圳大学 5 个核心业务(体育场馆预约、畅课任务、公文通、课表、考试安排)及深大知识库封装为 **6 Skill + 1 本地 KB**。外部 AI Agent(ChatGPT / Claude Code / OpenClaw 等)通过 Skill/MCP 协议调用，即刻具备"懂深大"的操作能力，无需自行实现浏览器自动化或理解各平台登录结构。6 Skill + 1 KB 即项目交付的**工具集全集**。

### §2.2 工具集 vs 智能助手边界声明

| 立场 | 出处 |
|---|---|
| ✅ 项目是**工具集**——提供 6 Skill + 1 KB，供外部 Agent 通过 Skill/MCP 协议调用 | 维持 |
| ❌ 项目**不是** AI Agent——不做 NLU / 意图识别 / 对话管理 | ADR-0001 D1 |
| ✅ "智能助手"是 **Agent 的能力**——足够多的 Skills + KB 让外部 Agent 变"懂深大的助手" | 新增 |
| ❌ 项目本身**没有"理解"**——Agent 收到"今天吃什么?"，Agent 理解意图，Agent 决定调 `kb_query`，KB 仅返回事实片段，Agent 生成回答 | 边界声明 |

此边界不可或缺：若项目实现 NLU，则自身成为 AI Agent，与 ADR-0001 冲突，并偏离课程"工具型 Java 项目"评估口径。项目职责是**提供足够丰富、足够可靠的工具**，"智能助手"所体现的理解与决策能力完全由外部 Agent 承担。本报告后续"智能助手"均指集成了本工具的外部 Agent。

### §2.3 6 Skill + 1 KB 一览

| # | Skill | 接入 | 优先级 |
|---|---|---|---|
| 1 | `booking_venue` 体育场馆预约 | ehall/CAS + Playwright | ✅ P0 |
| 2 | `chaoxing_tasks` 畅课任务 | 学习通 SSO + Playwright | P1 |
| 3 | `notice_list` 公文通通知 | ehall/CAS + Playwright | P1 |
| 4 | `schedule_get` 个人课表 | ehall/CAS + Playwright | P1 |
| 5 | `calendar_get` 校历 | 公开页 + Playwright | P1 |
| 6 | `exam_list` 考试安排 | ehall/CAS + Playwright | P1 |
| 7 | `kb_query` 深大知识库 | 本地 Markdown + 定期更新脚本 | P1 |

各 Skill 详细设计见 §3；知识库以本地 Markdown 为主体，通过定期脚本从学校官网等页面抓取更新。

## 三、P1 详细设计:6 Skill + 1 KB

[占位]

## 四、P0 现状: `book` Skill 作为首个落地

[占位]

## 五、设计模式应用(4 种, 贯穿 P0+P1)

[占位]

## 六、编程技术应用(6 种, 贯穿 P0+P1)

[占位]

## 七、测试与覆盖率

[占位]

## 八、局限性分析与改进

[占位]

## 九、总结与展望

[占位]
