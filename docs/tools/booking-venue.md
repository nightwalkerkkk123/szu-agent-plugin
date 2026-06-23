# `booking_venue` 工具操作文档

> 本工具用于深圳大学体育场馆预约。调用前请务必阅读本文，避免参数错误导致预约失败或误占名额。

## 重要提醒

- **真实预约会占用实际名额**，调用前必须获得用户明确确认。
- 本工具**不支持 dry-run**，一旦调用成功即视为真实提交。
- 同账号同时段同项目通常只能预约一次，重复调用可能失败或产生重复预约。
- 如果用户没给学号，默认使用当前已配置的账号（环境变量 `SZU_USERNAME`）。当前默认学号为 `2023150090`。

## 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 否 | 学号，例如 `2023150090`。若未提供，默认使用环境变量 `SZU_USERNAME` 配置的账号 |
| `campus` | string | 是 | 校区枚举名：`YUEHAI`（粤海）或 `LIHU`（丽湖） |
| `sport` | string | 是 | 运动项目枚举名，见下方完整列表 |
| `date` | string | 是 | ISO 8601 日期，例如 `2026-06-24` |
| `timeSlot` | string | 是 | `HH:mm-HH:mm` 格式，只支持整点 1 小时时段（08:00-22:00），例如 `16:00-17:00` |
| `preferredVenue` | integer | 否 | 1-based 偏好序号，默认 1。球场类指第几个可预约球场；健身房类指第几个可用容量时段/区域 |

## `sport` 枚举名完整列表

### 粤海校区（`campus=YUEHAI`）

- `BADMINTON`：羽毛球
- `FOOTBALL`：足球
- `VOLLEYBALL`：排球
- `TENNIS`：网球
- `BASKETBALL`：篮球
- `SQUASH`：壁球
- `GYM_HEAVY`：一楼重量型健身（即“一楼健身房”）
- `GYM_AEROBIC`：二楼有氧健身（即“二楼健身房”）
- `SWIMMING`：游泳

### 丽湖校区（`campus=LIHU`）

- `BADMINTON`：羽毛球
- `VOLLEYBALL`：排球
- `TENNIS`：网球
- `BASKETBALL`：篮球
- `SWIMMING`：游泳
- `TABLE_TENNIS`：乒乓球
- `DANCE`：舞蹈
- `POOL`：桌球
- `CYCLING`：骑行
- `MAGIC_MIRROR`：魔镜
- `BOARD_GAME`：桌游
- `GYM`：健身房
- `YOGA`：瑜伽
- `PICKLEBALL`：匹克球
- `SHUTTLECOCK`：毽球

## 常见自然语言映射

| 用户说法 | 应传参数 |
|----------|----------|
| “一楼健身房”（粤海） | `campus=YUEHAI`, `sport=GYM_HEAVY` |
| “二楼健身房”（粤海） | `campus=YUEHAI`, `sport=GYM_AEROBIC` |
| “健身房”（丽湖） | `campus=LIHU`, `sport=GYM` |
| “4-5 点” / “下午 4 点到 5 点” | `timeSlot="16:00-17:00"` |
| “明天” | `date` 取明天的 ISO 日期 |
| “后天” | `date` 取后天的 ISO 日期 |

## 示例调用

### 粤海一楼健身房 明天 16:00-17:00

```json
{
  "name": "booking_venue",
  "arguments": {
    "username": "2023150090",
    "campus": "YUEHAI",
    "sport": "GYM_HEAVY",
    "date": "2026-06-24",
    "timeSlot": "16:00-17:00"
  }
}
```

## 常见错误

1. **timeSlot 传成对象**：之前 schema 误写为对象，现已修正为字符串。不要再传 `{"start":"16:00","end":"17:00"}`。
2. **健身房枚举名选错**：粤海一楼是 `GYM_HEAVY`，不是 `GYM`（`GYM` 是丽湖的）。
3. **timeSlot 格式不标准**：例如 `4-5`、`16:00~17:00`、`下午4点-5点` 都会解析失败。
4. **未获得用户确认就调用**：真实预约会占名额，必须先向用户确认意图和具体参数。

## 失败排查

如果调用失败：

1. 检查参数是否严格符合本文说明。
2. 查看返回的 `errorCode` 和 `errorMessage`。
3. 记录 `traceId`，到 `logs/serve.log` 中搜索该 traceId 查看详细日志。
