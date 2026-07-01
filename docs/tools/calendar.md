# calendar_get

查询深圳大学校历,返回本学期的关键时间节点(开学、节假日、考试周、毕业、暑假等)。
重要约束(必须遵守,否则调用会失败或返回空):
1. 当前实现是静态 MVP:只内置了 2025-2026 学年的春季学期(2026-03-04 至 2026-07-17)数据,没有 IO、无需登录、无浏览器。
2. academicYear 可选,默认按当前系统日期推断(1-7 月取上一学年,8-12 月取本学年)。传其他值(如 "2024-2025")会返回空列表,而非报错 — 调用方应自行判断是否需要重试。
3. 学年格式严格为 "YYYY-YYYY",中间用半角连字符,例如 "2025-2026"。不要传 "2025" 或 "2025-2026 学年"。
4. 返回的事件类型 (type) 固定为下列枚举之一:SEMESTER_START / SEMESTER_END / EXAM_WEEK / BREAK / HOLIDAY。LLM 不应臆造新类型。
5. 不需要 username、密码或浏览器 cookie — 这是纯静态查询工具。
6. 调用前无需用户授权,因为不涉及任何账号行为或写操作。
7. 适合回答"这学期什么时候开学?"、"什么时候放暑假?"、"期末考试第几周?"等问题。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `academicYear` | 否 | `string` | 学年,格式 YYYY-YYYY,例如 2025-2026。可选,不传则按当前日期推断。 | pattern=^\d{4}-\d{4}$; examples=[2025-2026, 2024-2025] |

## 示例

```json
{
  "name": "calendar_get",
  "arguments": {
    "academicYear": "2025-2026"
  }
}
```

```json
{
  "name": "calendar_get",
  "arguments": {
    "academicYear": "2024-2025"
  }
}
```

```json
{
  "name": "calendar_get",
  "arguments": {}
}
```

```json
{
  "name": "calendar_get",
  "arguments": {
    "academicYear": "2023-2024"
  }
}
```

```json
{
  "name": "calendar_get",
  "arguments": {
    "academicYear": "2026-2027"
  }
}
```

## 返回值

```text
List<AcademicEvent>:
- date: LocalDate(ISO 8601,例如 "2026-03-04")
- type: 枚举 {SEMESTER_START, SEMESTER_END, EXAM_WEEK, BREAK, HOLIDAY}
- description: 中文一句话,例如 "本科生第十七周上课结束"
- semesterTag: 内部标记,例如 "2025-2026-SPRING",可用于过滤
静态 MVP 范围内(2025-2026 春季)固定返回 20+ 条事件。其他学年返回 []。
```

## 常见错误

- 传 academicYear="2025"(缺后缀)→ 返回 [];应改为 "2025-2026"
- 问"明天有什么校历事件"→ calendar_get 不支持单日查询;用 date 过滤结果
- 传不存在的学年(如 "2099-2100")→ 返回 [] 而非报错;调用方应自行 fallback 到默认年

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `calendar_get`

