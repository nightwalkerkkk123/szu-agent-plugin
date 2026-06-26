# MCP 工具参考文档

> 由 [`ToolDocsGenerator`](../../src/main/java/edu/szu/agent/task/ToolDocsGenerator.java)
> 从 `CampusTask` 的 `description()` / `inputSchema()` / `annotations()` 渲染生成。
> 与 MCP `tools/list` JSON 输出**共用同一份数据源**,保证机器/人类两路一致。

## 文件清单

| 工具 | 渲染源 | 详细文档 | examples 个数 |
|---|---|---|---:|
| `calendar_get` | `CalendarTask` | [calendar.md](calendar.md) | 5 |
| `kb_query` | `KnowledgeTask` | [kb-query.md](kb-query.md) | 7 |
| `schedule_list` | `ScheduleListTask` | [schedule-list.md](schedule-list.md) | 2 |
| `notice_list` | `NoticeTask` | [notice-list.md](notice-list.md) | 6 |
| `exam_list` | `ExamListTask` | [exam-list.md](exam-list.md) | 5 |
| `homework_list` | `HomeworkTask` | [homework-list.md](homework-list.md) | 3 |
| `homework_download` | `HomeworkDownloadTask` | [homework-download.md](homework-download.md) | 5 |
| `booking_venue` | `BookingTask` | [booking-venue.md](booking-venue.md) | 7 |

## 章节结构

每份参考文档包含 6 个章节,与 `ToolDocsGenerator.renderMarkdown(Skill<?>)` 的渲染顺序一一对应:

1. **标题 + 概述** —— 来自 `task.description().strip()`
2. **参数** —— 来自 `inputSchema().properties`,包含 `type` / `required` / `description` / 约束(`enum` / `format` / `pattern` / `default` / `minimum` / `examples`)
3. **枚举** —— 仅当存在 `enum` 字段时生成,把每个枚举参数展平成单行 `|` 可选值 |` 表格
4. **示例** —— 来自 `annotations().examples()`,1-3 个 `{"name": "...", "arguments": {...}}` JSON 块
5. **返回值** —— 来自 `annotations().resultShape()`
6. **常见错误** —— 来自 `annotations().commonErrors()`
7. **相关文档** —— 固定指向 [MCP.md](../../MCP.md)

## 重新生成

```bash
mvn -q -DskipTests package
java -cp target/szu-agent-plugin.jar edu.szu.agent.task.ToolDocsGenerator
```

> **不挂 CI**:为避免每次 `mvn test` 都产生 docs 文本 diff,生成只用于首次落地或大版本变更。
> 日常修改某个工具的 `description()` / `inputSchema()` / `annotations()` 后,手工 review + 重新生成。

输出格式:每份工具文档以 `<!-- tool-doc: <name> -->` 标记开头,后接渲染好的 Markdown。
可用 Python 一行拆分为 8 个文件:

```python
import pathlib
output = pathlib.Path('/tmp/tool-docs-output.md').read_text(encoding='utf-8')
mapping = {
    'booking_venue': 'booking-venue.md', 'calendar_get': 'calendar.md',
    'exam_list': 'exam-list.md', 'homework_download': 'homework-download.md',
    'homework_list': 'homework-list.md', 'kb_query': 'kb-query.md',
    'notice_list': 'notice-list.md', 'schedule_list': 'schedule-list.md',
}
docs_dir = pathlib.Path('docs/tools')
for block in output.split('<!-- tool-doc: ')[1:]:
    name, body = block.split(' -->', 1)
    (docs_dir / mapping[name.strip()]).write_text(body.lstrip(), encoding='utf-8')
```

## 测试覆盖

`src/test/java/edu/szu/agent/task/ToolDocsGeneratorTest.java` 包含 3 个用例:

- `renderMarkdownIncludesCoreSections` —— 验证 `booking_venue` 渲染包含 6 个必备章节
- `renderMarkdownHandlesEmptyAnnotations` —— 验证 `annotations = empty()` 时降级为"暂无"占位
- `allBuiltInSkillsRenderCompleteDocs` —— 验证 8 个内置 Skill 全部渲染产物长度 > 300 字符

`src/test/java/edu/szu/agent/mcp/ToolSchemaTest.java` 进一步校验机器侧:

- `tools/list` 返回 `schemaVersion = "1.3"` 且 `tools.length == 8`
- 每个 tool envelope 含非空 `examples` 顶层数组
- `description.length() > 50`(防止 description 被偷工减料)
- 每个 tool 的 `required` / `enum` / `pattern` / `format` / `default` / `minimum` 与 `annotations` 中的提示严格对应
