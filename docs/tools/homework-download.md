# homework_download

下载深圳大学畅课(LMS/超星)单个作业的全部附件到本地目录。
重要约束(必须遵守,否则调用会失败或写错位置):
1. homeworkId 是必填,必须来自 homework_list 返回的 Homework.homeworkId。不要传课程名、作业标题或 URL。
2. outputDir 是必填,表示本机输出目录。MCP 宿主/daemon 运行在哪台机器,文件就写到那台机器的该目录;
   不要把云盘 URL、HTTP URL 或用户看不见的相对路径当成本地目录。
3. username 可选;若不传,读取环境变量 SZU_USERNAME。若两者都没有,抛 Missing required parameter: username。
4. 这是需要账号态的真实下载路径:会解析 SZU_PASSWORD_<学号> 或 --env-file 凭证,并复用 30 天会话。
   不要在 MCP 参数中传密码、cookie 或 token。
5. throttleMs 可选,默认 500ms,建议保持默认以避免对 LMS 高频访问。maxRetries 可选,默认 2,必须 >= 0。
6. 返回结果是 sealed HomeworkDownloadResult: Success(attachments)、Empty(homeworkId)、Failure(code,message)。
   Empty 表示该作业没有附件,不是错误,不要自动重试。
7. 适合用户明确要求"下载这个作业附件"且已知道 homeworkId 时调用;不知道 homeworkId 时先调用 homework_list。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `username` | 否 | `string` | 深大学号,11 位数字,例如 2023150090。可选;不传则读取 SZU_USERNAME。 | pattern=^20\d{9}$; examples=[2023150090] |
| `homeworkId` | 是 | `string` | 作业编号,必须来自 homework_list 返回的 homeworkId。必填。 | pattern=^\d+$; examples=[169193] |
| `outputDir` | 是 | `string` | 本机输出目录路径。必填。建议传绝对路径。 | format=uri-reference; examples=[/Users/wangzihao/Downloads/szu-homework] |
| `throttleMs` | 否 | `integer` | 附件之间下载间隔毫秒,默认 500。必须 >= 0。 | default=500; minimum=0; examples=[500, 1000] |
| `maxRetries` | 否 | `integer` | 单个附件最大重试次数,默认 2。必须 >= 0。 | default=2; minimum=0; examples=[2, 3] |

## 示例

```json
{
  "name": "homework_download",
  "arguments": {
    "username": "2023150090",
    "homeworkId": "169193",
    "outputDir": "/Users/wangzihao/Downloads/szu-homework"
  }
}
```

```json
{
  "name": "homework_download",
  "arguments": {
    "homeworkId": "169193",
    "outputDir": "./downloads/homework-169193",
    "throttleMs": 1000,
    "maxRetries": 3
  }
}
```

```json
{
  "name": "homework_download",
  "arguments": {
    "username": "2023150090",
    "homeworkId": "177533",
    "outputDir": "/tmp/szu-attachments",
    "throttleMs": 0,
    "maxRetries": 0
  }
}
```

```json
{
  "name": "homework_download",
  "arguments": {
    "username": "2030200100",
    "homeworkId": "200001",
    "outputDir": "/Users/other/Downloads/szu"
  }
}
```

```json
{
  "name": "homework_download",
  "arguments": {
    "homeworkId": "169193",
    "outputDir": ".",
    "throttleMs": 1500
  }
}
```

## 返回值

```text
HomeworkDownloadResult (sealed):
- Success { attachments: List<HomeworkAttachment> }
- Empty { homeworkId: String } // 无附件,不是错误
- Failure { code: ErrorCode, message: String }
HomeworkAttachment 字段:
- homeworkId, fileName, sourceUrl
- localPath: 下载后的本地绝对路径
- sizeBytes: 文件大小
- downloadedAt: 完成时间 Instant
```

## 常见错误

- 缺 homeworkId/outputDir → INVALID_REQUEST;两者都是必填
- 传作业标题而非数字 homeworkId → homeworkId must not be blank/下载失败;先用 homework_list 查 id
- 未注入凭证或会话过期 → ACCOUNT_RESOLUTION_FAILED/SESSION_EXPIRED;需 env 或 headed 登录刷新

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `homework_download`

