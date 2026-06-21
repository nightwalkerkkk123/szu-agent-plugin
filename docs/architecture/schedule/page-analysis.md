# 课表页面结构分析 (Schedule Page Analysis)

**Date:** 2026-06-18
**Source:** `https://ehall.szu.edu.cn/jwapp/sys/wdkb/*default/index.do?t_s=...&_sec_version_=...&gid_=...&EMAP_LANG=zh&THEME=cherry#/xskcb`
**Related:** [Plan](../../plans/PLAN-schedule.md) · [ADR-0009](../../adr/0009-schedule-module-design.md)

---

## 1. URL 构成

| 段 | 值 | 说明 |
|---|---|---|
| 协议 | `https://` | 标准 TLS |
| 主机 | `ehall.szu.edu.cn` | 深大 ehall 统一门户 |
| 路径 | `/jwapp/sys/wdkb/*default/index.do` | ehall 微应用入口,`*default` 是默认视图 |
| Query | `t_s`, `_sec_version_`, `gid_`, `EMAP_LANG`, `THEME` | `t_s` 是时间戳,`_sec_version_` / `gid_` 是 ehall session 衍生 token,`EMAP_LANG=zh` 是语言,`THEME=cherry` 是 UI 主题 |
| Hash | `#/xskcb` | 应用内 hash 路由,xskcb = 学生课表 |

**关键点:** hash 路由 `#/xskcb` 是页面主键。query 里的 `t_s` / `gid_` / `_sec_version_` 在第一次进入 ehall 时由 ehall 自己注入,**不需手动拼**。Playwright 通过 `BrowserLifecycle.navigateTo(EHALL_SCHEDULE_URL)` 即可。

---

## 2. 整体结构

页面是单一 `table.wut_table` 8 行 × 8 列的网格。带课程时单元格内含一个 `div.mtt_arrange_item`,无课时为空。

### 2.1 表格骨架

```html
<table class="wut_table" style="table-layout: fixed;" onselectstart="return false">
  <tbody>
    <!-- 行 0: 星期表头 -->
    <tr>
      <td class="mtt_bgcolor_grey">节次/星期</td>
      <td class="mtt_bgcolor_grey" data-week="7">星期日</td>
      <td class="mtt_bgcolor_grey" data-week="1">星期一</td>
      <td class="mtt_bgcolor_grey" data-week="2">星期二</td>
      <td class="mtt_bgcolor_grey" data-week="3">星期三</td>
      <td class="mtt_bgcolor_grey" data-week="4">星期四</td>
      <td class="mtt_bgcolor_grey" data-week="5">星期五</td>
      <td class="mtt_bgcolor_grey" data-week="6">星期六</td>
    </tr>
    <!-- 行 1-8: 节次数据 -->
    <tr>
      <td class="mtt_bgcolor_grey" data-unit="1"><div>1-2节</div></td>
      <td data-role="item" data-week="7" data-begin-unit="1" data-end-unit="2"></td>  <!-- 空 -->
      <td data-role="item" data-week="3" data-begin-unit="1" data-end-unit="2">
        <div class="mtt_arrange_item" style="background-color:#FFF0CC;...">
          <div class="mtt_item_tzkcicon"></div>
          <div class="mtt_item_kcmc">操作系统[05]</div>
          <div class="mtt_item_jxbmc">杜智华</div>
          <div class="mtt_item_room">1-17周,星期3,1-2节,致理楼L1-601</div>
        </div>
      </td>
      ...
    </tr>
    ...
  </tbody>
</table>
```

### 2.2 8 行 × 8 列定义

**列(星期表头,1-7 编号,7=周日):**

| `data-week` | 中文 | JS `Day` |
|---|---|---|
| 7 | 星期日 | 0 |
| 1 | 星期一 | 1 |
| 2 | 星期二 | 2 |
| 3 | 星期三 | 3 |
| 4 | 星期四 | 4 |
| 5 | 星期五 | 5 |
| 6 | 星期六 | 6 |

注意 SZU ehall 的星期编码是 **1=周一, 7=周日**(ISO 8601 / Java `DayOfWeek` 风格),与 ISO 8601 数字一致。

**行(节次表头):**

| 行号 | `data-unit` | 显示 | 含义 |
|---|---|---|---|
| 0 | — | 节次/星期 | 表头 |
| 1 | 1 | 1-2节 | 上午第 1 节课 |
| 2 | 2 | 3-4节 | 上午第 2 节课 |
| 3 | 3 | 5-5节 | 下午第 1 节课(单节) |
| 4 | 4 | 6-6节 | 下午第 2 节课(单节) |
| 5 | 5 | 7-8节 | 下午第 3 节课 |
| 6 | 6 | 9-10节 | 晚间第 1 节课 |
| 7 | 7 | 11-12节 | 晚间第 2 节课 |
| 8 | 8 | 13-14节 | 晚上延长段 |

共 **8 个节次行**。节次 `5-5节` / `6-6节` 是单节(45 分钟),其余是双节连排(2×45=90 分钟)。

### 2.3 单元格属性

每个数据格 `td[data-role="item"]` 自带定位三件套:
- `data-week` — 星期 (1-7)
- `data-begin-unit` — 起始节次
- `data-end-unit` — 结束节次
- `class="highLight"` — **当前选中日**(`schedule today` 子功能用)

无课时,单元格为空,只有 3 个 `data-*` 属性。
有课时,单元格内含一个 `div.mtt_arrange_item`。

---

## 3. 课程块四要素 (`div.mtt_arrange_item`)

| CSS 类 | 内容类型 | 示例 |
|---|---|---|
| `.mtt_item_kcmc` | 课程名 + 教学班号 | `操作系统[05]` |
| `.mtt_item_jxbmc` | 任课教师 | `杜智华` |
| `.mtt_item_room` | 复合文本(周次+星期+节次+教室) | `1-17周,星期3,1-2节,致理楼L1-601` |
| `.mtt_item_tzkcicon` | 调停课状态(空 = 正常,有内容 = 调/停) | (空) / `调` / `停` |
| `style="background-color:#XXXXXX"` | 课程底色(每门课不同) | `#FFF0CC` / `#D3EAFD` / `#FFDDD3` / `#D3F4F8` |

### 3.1 `.mtt_item_room` 文本解析

格式严格:**`{周次表达式}周,星期{N},{begin-end}节,{教室}`**

样例:
- `1-17周,星期3,1-2节,致理楼L1-601` — 连排周次
- `1-8,10-17周,星期2,7-8节,致理楼L1-711` — 跳过周次
- `1-17周(单),星期3,1-2节,...` — 单周(可能后缀)
- `1-17周(双),星期3,1-2节,...` — 双周(可能后缀)

用 split 即可拆出 4 段,再分别用 `WeekRangeParser` / `PeriodMapping` 解析。

### 3.2 课程名拆分

`.mtt_item_kcmc` 格式:`{课程名}[{教学班号}]`

样例:
- `操作系统[05]` → `courseName="操作系统"`, `section="05"`
- `面向对象高级编程专题[01]` → `courseName="面向对象高级编程专题"`, `section="01"`
- `多媒体系统导论[02]` → 同上

正则 `^(.+?)\[(\d+)\]$` 拆。理论上不命中格式时回退:`courseName=全文`,`section=null`。

### 3.3 调停课标记

`.mtt_item_tzkcicon`:
- 元素存在但 `textContent` 为空 → 正常课程,`isAdjusted=false`
- 元素有非空内容(可能含"调"/"停"等字符) → 调停课,`isAdjusted=true`

`isAdjusted` 字段在 MVP 中不输出到 JSON(MVP 只关心"今天有什么课"),留为 P1 字段或加 `--show-adjusted` flag。

### 3.4 底色 `style`

`background-color` 是 ehall 给每门课分配的主题色,**不携带业务含义**,仅 UI 用途。MVP 不提取。如需"按课程聚合查看"功能可作为去重 key。

---

## 4. 当前选中日识别 (highLight)

`td[data-role="item"].highLight` 表示当前 ehall 默认高亮的那一列(通常是今天)。

- ehall 本身有"上一周/下一周"按钮,选中日随之变化
- **MVP:** 假设打开页面默认是本周,且 `highLight` 列 = 今天
- **未来:** 可加 `--week` 参数(`prev` / `current` / `next`),通过点击 ehall 周次切换按钮实现

`schedule today` 子功能(MVP 通过 `--today` flag 实现)逻辑:
1. 抓所有 `td.highLight[data-role="item"]`
2. 取 `data-week` 作为今日星期
3. 过滤 `CourseEntry.weekday == Weekday.of(todayWeek)`
4. 输出到 JSON 的 `data.today.courses`

---

## 5. 周次表达式 (WeekRange)

`WeekRangeParser` 必须支持的格式(根据历史 LMS 课表 + 排课系统常识):

| 输入 | 输出 | 说明 |
|---|---|---|
| `1-17周` | `[1..17]` | 连排周次(最常见) |
| `1-8,10-17周` | `[1..8, 10..17]` | 跳过周次(国庆等假期) |
| `1-17周(单)` | `[1,3,5,...,17]` | 单周 |
| `1-17周(双)` | `[2,4,6,...,16]` | 双周 |

`WeekRange.contains(int week)` 方法用于:
- "今天(第 N 周)是否有这门课"过滤
- 配合 `LocalDate.now()` 推算"当前是第几周"(需要学期起始日,留 P1)

**学期第几周推算:** 课表页本身不显示"当前周数"。MVP 假设总是第 1-17 周中所有周次都有效;P1 通过学期起始日 + `LocalDate.now()` 计算当前周,然后 `WeekRange.contains(currentWeek)` 过滤。

---

## 6. 节次时刻表 (Period)

`PeriodMapping` 静态常量表(MVP 占位,P1 校准):

| 节次键 | beginUnit | endUnit | 推测起止时间 | 备注 |
|---|---|---|---|---|
| `1-2` | 1 | 2 | 08:00 – 09:50 | 上午第一节 |
| `3-4` | 3 | 4 | 10:10 – 12:00 | 上午第二节 |
| `5-5` | 5 | 5 | 14:00 – 14:50 | 下午第一节(单节) |
| `6-6` | 6 | 6 | 15:00 – 15:50 | 下午第二节(单节) |
| `7-8` | 7 | 8 | 16:10 – 17:50 | 下午第三节 |
| `9-10` | 9 | 10 | 19:00 – 20:50 | 晚间第一节 |
| `11-12` | 11 | 12 | 21:00 – 22:50 | 晚间第二节 |
| `13-14` | 13 | 14 | (待校准) | 晚上延长段(可能不常用) |

**时间值来源:** 经验值,未在 ehall 页面上确认(页面只显示节次不显示时间)。MVP 文档明确"占位常量,P1 校准",**不影响 `schedule list` 的核心功能**。

---

## 7. 抓取策略

### 7.1 注入 JS 一次性抓取

```javascript
() => Array.from(document.querySelectorAll('td[data-role="item"]'))
  .filter(td => td.querySelector('.mtt_arrange_item'))
  .map(td => {
    const item = td.querySelector('.mtt_arrange_item');
    return {
      courseName:  item.querySelector('.mtt_item_kcmc')?.textContent.trim() ?? null,
      teacher:     item.querySelector('.mtt_item_jxbmc')?.textContent.trim() ?? null,
      roomText:    item.querySelector('.mtt_item_room')?.textContent.trim() ?? null,
      isAdjusted:  (item.querySelector('.mtt_item_tzkcicon')?.textContent.trim() || '').length > 0,
      weekday:     parseInt(td.dataset.week, 10),
      beginUnit:   parseInt(td.dataset.beginUnit, 10),
      endUnit:     parseInt(td.dataset.endUnit, 10),
      isToday:     td.classList.contains('highLight')
    };
  });
```

返回 JSON 数组,Java 端用 Jackson `TypeReference<List<RawCourse>>` 反序列化。

### 7.2 Java 端处理

1. 反序列化为 `RawCourse { courseName, teacher, roomText, isAdjusted, weekday, beginUnit, endUnit, isToday }`
2. 遍历每条,解析 `roomText` → 拆出 `WeekRange` / `weekday` / `period` / `room` 4 段
3. 用正则拆 `courseName` → `name` + `section`
4. 构造 `CourseEntry`
5. `List.copyOf()` 返回不可变 List

### 7.3 等待表格加载

`NavigateToScheduleStep` 完成后,需等 `table.wut_table` 可见:

```java
Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(10));
wait.until(ExpectedConditions.visibilityOfElementLocated(
    By.cssSelector("table.wut_table")));
```

(实际用项目内 `BrowserLifecycle.isVisible(selector)` + 轮询,参考 `SelectTimeSlotStep.waitForVisible`。)

---

## 8. 风险与注意事项

| 风险 | 说明 | 缓解 |
|---|---|---|
| 课表 SPA 懒加载 | ehall 课表是 SPA,需等 `table.wut_table` 真正渲染出来 | `waitForVisible` 轮询,超时抛 `SCHEDULE_PAGE_LOAD_FAILED` |
| 周次表达式变体 | 实际可能遇到 "1-17(单)周" 或 "1-17周 单" 等变体 | `WeekRangeParser` 用宽松正则 + 多步尝试 |
| 跨学期/单双周显示 | SZU 实际是固定 18 周一学期,单双周由课程本身决定 | MVP 总是返回所有周次,`--week` 过滤留 P1 |
| ehall 反爬 | ehall 是学校内部系统,无明显反爬,Playwright 真跑即可 | 不需要特殊处理 |
| 课程合并单元格 | 当前 HTML 每个时段独立 `<td>`,未发现 `rowspan/colspan` | 不需特殊处理(已验证) |
| 调停课状态文本 | `.mtt_item_tzkcicon` 文本可能多语言/多符号 | 只判断非空,具体文字留 P1 |
| 复选课/辅修课 | 课表只显示主修 + 已选中的辅修 | 数据完整性由 ehall 决定,我们只做映射 |

---

## 9. 不在分析范围

- 课程详情页(点击课程名跳转后的页面)
- 成绩页、考试安排页(独立 URL)
- 学期切换按钮(已发现但 MVP 不实现自动化)
- 调停课的具体文字解析(只判断有无)

---

## 10. 引用

- **Plan:** `docs/plans/PLAN-schedule.md`
- **ADR:** `docs/adr/0009-schedule-module-design.md`
- **历史参考:** `docs/stories/US-006-chaoxing-homework-list.md` 的 Notes 章节
- **Python 参考实现:** `E:\CODE\szu-sports-booking\`(提供对深大系统的领域知识)
