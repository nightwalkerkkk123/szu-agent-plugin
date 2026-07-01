# exam_list

查询深圳大学考试安排列表,返回课程考试日期、时间、地点、课程代码和监考教师。
重要约束(必须遵守,否则调用会失败或返回空):
1. username 是必填字段。当前实现使用静态 HTML 快照解析,不登录教务系统,但保留 username 以对齐
   未来真实抓取路径和 MCP/CLI 参数契约。
2. status 可选,枚举值固定两个中文字符串:"待开始考试" 或 "已结束"。不要传英文 PENDING、FINISHED,
   也不要传"未开始"、"已考完"等同义词。
3. 不传 status 表示返回全部考试安排;传 status 后按 examDate 与当前日期比较过滤。
4. 返回的 ExamSchedule 包含 date(原始中文月日)、weekday、courseName、courseCode、examDate、
   startTime、endTime、venue、invigilator。调用方可自行按课程名/日期做二次筛选。
5. 当前静态 MVP 不保证实时更新,也不支持按学期、课程名或周次作为服务器端参数过滤。
6. 适合回答"我有哪些考试?"、"操作系统考试在哪?"、"还有哪些未开始考试?"等问题。
7. 如果用户只说"考试安排",只传 username;不要为了过滤而臆造 status。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `username` | 是 | `string` | 深大学号,11 位数字,例如 2023150090。必填。 | pattern=^20\d{9}$; examples=[2023150090] |
| `status` | 否 | `string` | 考试状态筛选。不传则返回全部。只能是中文枚举: 待开始考试 / 已结束。 | enum=[待开始考试, 已结束]; examples=[待开始考试, 已结束] |

## 枚举

| 参数 | 可选值 |
|---|---|
| `status` | `待开始考试`, `已结束` |

## 示例

```json
{
  "name": "exam_list",
  "arguments": {
    "username": "2023150090"
  }
}
```

```json
{
  "name": "exam_list",
  "arguments": {
    "username": "2023150090",
    "status": "待开始考试"
  }
}
```

```json
{
  "name": "exam_list",
  "arguments": {
    "username": "2023150090",
    "status": "已结束"
  }
}
```

```json
{
  "name": "exam_list",
  "arguments": {
    "username": "2030200100"
  }
}
```

```json
{
  "name": "exam_list",
  "arguments": {
    "username": "2023150090",
    "status": "已结束"
  }
}
```

## 返回值

```text
List<ExamSchedule>:
- date: 原始月日文本,例如 "7月14日"
- weekday: 星期文本,例如 "星期二"
- courseName/courseCode: 课程名与课程代码
- examDate: LocalDate(ISO 8601)
- startTime/endTime: LocalTime
- venue: 考试地点
- invigilator: 监考教师
```

## 常见错误

- status="未开始" → 当前实现不会过滤;应传 "待开始考试"
- 缺 username → INVALID_REQUEST;必须传学号
- 用户按课程名查询 → exam_list 不支持 courseName 参数;先取列表再由调用方过滤

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `exam_list`

