# homework_list

查询深圳大学畅课(LMS/超星)待办作业列表,返回课程名、作业标题、截止时间和提交状态。
重要约束(必须遵守,否则调用会失败或触发真实账号流程):
1. username 可选;若不传,默认读取环境变量 SZU_USERNAME。若两者都没有,抛
   IllegalArgumentException("Missing required parameter: username")。
2. 这是需要账号态的真实路径:会通过 AccountResolver 解析 SZU_PASSWORD_<学号> 或 --env-file 注入凭证,
   然后启动浏览器访问畅课。不要在 MCP 层存储密码,不要把密码放进 arguments。
3. 当前工具只返回作业列表,不下载附件。下载附件必须先从结果中取 homeworkId,再调用 homework_download。
4. 返回结果是 sealed HomeworkListResult: Success(homeworks) 或 Failure(code,message)。外部 Agent 应先判断
   success/failure 类型,不要假设一定有 homeworks。
5. 每条 Homework 含 homeworkId、courseName、title、deadline、status。deadline 是畅课页面原始字符串,
   通常形如 "2026.06.24 23:59",不是 ISO 日期。
6. 适合回答"我有哪些作业?"、"哪门课作业还没交?"、"作业什么时候截止?"等问题。
7. 若返回账号解析失败/会话过期,需要用户先配置 env 或完成一次 headed 登录;不要重试高频访问。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `username` | 否 | `string` | 深大学号,11 位数字,例如 2023150090。可选;不传则读取 SZU_USERNAME。 | pattern=^20\d{9}$; examples=[2023150090] |

## 示例

```json
{
  "name": "homework_list",
  "arguments": {
    "username": "2023150090"
  }
}
```

```json
{
  "name": "homework_list",
  "arguments": {}
}
```

```json
{
  "name": "homework_list",
  "arguments": {
    "username": "2030200100"
  }
}
```

## 返回值

```text
HomeworkListResult (sealed):
- Success { homeworks: List<Homework> }
- Failure { code: ErrorCode, message: String }
Homework 字段:
- homeworkId: 作业 id,供 homework_download 使用
- courseName: 课程名
- title: 作业标题
- deadline: 原始截止时间文本,例如 "2026.06.24 23:59"
- status: 状态文本,例如 待提交
```

## 常见错误

- 未传 username 且无 SZU_USERNAME → INVALID_REQUEST("Missing required parameter: username")
- 未注入 SZU_PASSWORD_<学号> → ACCOUNT_RESOLUTION_FAILED;需通过 env 或 --env-file 提供
- 想下载附件却调用 homework_list → 先取 homeworkId,再调用 homework_download

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `homework_list`

