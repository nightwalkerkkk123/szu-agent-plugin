# booking_venue

深圳大学体育场馆定时预约。重要约束(必须遵守,否则调用会失败):
1. 真实预约会占用实际名额,调用前必须获得用户明确确认。
2. campus 必须是枚举值之一:YUEHAI(粤海校区)或 LIHU(丽湖校区)。
3. sport 必须是枚举值之一,且要与 campus 匹配:
   - 粤海校区(YUEHAI)可选:BADMINTON(羽毛球),FOOTBALL(足球),VOLLEYBALL(排球),TENNIS(网球),BASKETBALL(篮球),SQUASH(壁球),GYM_HEAVY(一楼重量型健身/一楼健身房),GYM_AEROBIC(二楼有氧健身/二楼健身房),SWIMMING(游泳)。
   - 丽湖校区(LIHU)可选:BADMINTON(羽毛球),VOLLEYBALL(排球),TENNIS(网球),BASKETBALL(篮球),SWIMMING(游泳),TABLE_TENNIS(乒乓球),DANCE(舞蹈),POOL(桌球),CYCLING(骑行),MAGIC_MIRROR(魔镜),BOARD_GAME(桌游),GYM(健身房),YOGA(瑜伽),PICKLEBALL(匹克球),SHUTTLECOCK(毽球)。
4. date 必须是 ISO 8601 日期字符串,例如 2026-06-24。
5. timeSlot 必须是字符串,格式 HH:mm-HH:mm,只支持整点 1 小时时段,例如 16:00-17:00。不要传对象,不要写 4-5 点 等口语。
6. 若用户未提供 username,默认使用环境变量 SZU_USERNAME 配置的账号(当前默认 2023150090)。
7. 用户说 明天 时,date 取明天的 ISO 日期;用户说 4-5点/下午4点到5点 时,timeSlot 必须转换为 16:00-17:00。
8. 调用前必须先向用户复述校区、项目、日期、时段,得到明确同意后再执行。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `username` | 否 | `string` | 学号,例如 2023150090。若未提供,默认使用环境变量 SZU_USERNAME 配置的账号。 | pattern=^20\d{9}$; examples=[2023150090] |
| `campus` | 是 | `string` | 校区枚举名:YUEHAI(粤海)或 LIHU(丽湖)。 | enum=[YUEHAI, LIHU]; examples=[YUEHAI, LIHU] |
| `sport` | 是 | `string` | 运动项目枚举名。必须与 campus 匹配:粤海校区用 GYM_HEAVY/GYM_AEROBIC,丽湖校区用 GYM。详见 description 中的完整映射。 | enum=[BADMINTON, FOOTBALL, VOLLEYBALL, TENNIS, BASKETBALL, SQUASH, GYM_HEAVY, GYM_AEROBIC, SWIMMING, TABLE_TENNIS, DANCE, POOL, CYCLING, MAGIC_MIRROR, BOARD_GAME, GYM, YOGA, PICKLEBALL, SHUTTLECOCK]; examples=[TENNIS, GYM_HEAVY, GYM] |
| `date` | 是 | `string` | ISO 8601 日期,例如 2026-06-24。 | format=date; examples=[2026-06-24] |
| `timeSlot` | 是 | `string` | 预约时段,HH:mm-HH:mm 格式,只支持整点 1 小时时段(08:00-22:00),例如 16:00-17:00。禁止传对象。 | pattern=^([01]?[0-9]\|2[0-3]):00-([01]?[0-9]\|2[0-3]):00$; examples=[16:00-17:00, 19:00-20:00] |
| `preferredVenue` | 否 | `integer` | 1-based 偏好序号,默认 1。对球场类项目指第几个可预约球场;对健身房类容量项目指第几个可用容量时段/区域。 | default=1; minimum=1; examples=[1, 2] |

## 枚举

| 参数 | 可选值 |
|---|---|
| `campus` | `YUEHAI`, `LIHU` |
| `sport` | `BADMINTON`, `FOOTBALL`, `VOLLEYBALL`, `TENNIS`, `BASKETBALL`, `SQUASH`, `GYM_HEAVY`, `GYM_AEROBIC`, `SWIMMING`, `TABLE_TENNIS`, `DANCE`, `POOL`, `CYCLING`, `MAGIC_MIRROR`, `BOARD_GAME`, `GYM`, `YOGA`, `PICKLEBALL`, `SHUTTLECOCK` |

## 示例

```json
{
  "name": "booking_venue",
  "arguments": {
    "username": "2023150090",
    "campus": "YUEHAI",
    "sport": "TENNIS",
    "date": "2026-06-24",
    "timeSlot": "19:00-20:00",
    "preferredVenue": 1
  }
}
```

```json
{
  "name": "booking_venue",
  "arguments": {
    "campus": "YUEHAI",
    "sport": "GYM_HEAVY",
    "date": "2026-06-24",
    "timeSlot": "16:00-17:00"
  }
}
```

```json
{
  "name": "booking_venue",
  "arguments": {
    "campus": "LIHU",
    "sport": "GYM",
    "date": "2026-06-25",
    "timeSlot": "20:00-21:00"
  }
}
```

```json
{
  "name": "booking_venue",
  "arguments": {
    "username": "2023150090",
    "campus": "YUEHAI",
    "sport": "BADMINTON",
    "date": "2026-06-27",
    "timeSlot": "14:00-15:00",
    "preferredVenue": 2
  }
}
```

```json
{
  "name": "booking_venue",
  "arguments": {
    "campus": "LIHU",
    "sport": "BASKETBALL",
    "date": "2026-06-28",
    "timeSlot": "18:00-19:00"
  }
}
```

```json
{
  "name": "booking_venue",
  "arguments": {
    "campus": "YUEHAI",
    "sport": "SWIMMING",
    "date": "2026-06-29",
    "timeSlot": "09:00-10:00"
  }
}
```

```json
{
  "name": "booking_venue",
  "arguments": {
    "campus": "LIHU",
    "sport": "YOGA",
    "date": "2026-06-30",
    "timeSlot": "07:00-08:00"
  }
}
```

## 返回值

```text
BookingResult (sealed):
- Success { request: BookingRequest, venueName: String, confirmationNo: String, message: String }
- Failure { code: ErrorCode, message: String }
BookingRequest 字段: username, campus, sport, date, timeSlot, preferredVenueIndex。
注意:调用成功会真实占用预约名额,调用前必须获得用户明确确认。
```

## 常见错误

- sport 与 campus 不匹配(如 LIHU + GYM_HEAVY)→ INVALID_REQUEST;按 description 的校区映射修正
- timeSlot 传对象或口语 "4-5点" → INVALID_REQUEST;必须转为 "16:00-17:00"
- 未注入账号凭证/会话过期 → ACCOUNT_RESOLUTION_FAILED 或 SESSION_EXPIRED;需 env/--env-file 或 headed 登录刷新

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `booking_venue`

