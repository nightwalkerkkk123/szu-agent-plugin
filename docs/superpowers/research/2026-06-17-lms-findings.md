# 真实 LMS 抓包分析 — 2026-06-17

> **来源**: 用户手动抓包 `lms.szu.edu.cn2333.har` (36MB, 177 entries)
> **用途**: 校准 US-008 spec/plan 中 LMS 页面结构 + 下载机制的猜测
> **影响**: Task 8/9/11 实现时以此为准,不再是 spec §6 的猜测

---

## 1. 作业列表页结构 (US-006 验证)

**容器**: `.todo-list-container`

**每项 `.todo-item` 包含**:
- `.todo-icon use[xlink:href="#todo-homework"]` — 标识是作业 (非通知/非考试)
- `.todo-title .text-too-long` — 作业标题
- `.todo-status div` — 状态文字 (如 `待提交`)
- `.todo-course .text-too-long` — 课程名
- `.todo-datetime` — 截止时间文本,前缀 `截止时间:`,格式 `YYYY.MM.DD HH:mm`
- `.todo-actions a.todo-link` — 链接

**`a.todo-link` href 格式**:
```
/course/<courseId>/learning-activity#/<homeworkId>?view=scores
```

**真实示例**:
- `/course/24085/learning-activity#/169193?view=scores` — 操作系统 / 综合实验二
- `/course/24180/learning-activity#/177533?view=scores` — 面向对象高级编程专题 / 期末大作业提交
- `/course/24170/learning-activity#/185895?view=scores` — 多媒体系统导论 / 实验5
- `/course/24170/learning-activity#/185894?view=scores` — 多媒体系统导论 / 第九、十讲编程作业

**注**:URL 是 hash 路由 (`#/169193`),不是查询参数。`?view=scores` 是详情页内部视图切换。

---

## 2. 作业详情页结构 (US-008 核心发现)

**真实结构** (从抓包 HTML 解析):

```html
<div class="attachment-row preview-able clearfix ng-scope" ...>
    <div class="attachment-column column large-10">
        <div class="w-full flex">
            <i class="font font-file-word left"></i>  <!-- 文件类型图标 -->
            <span class="file-name-wrapper">
                <span class="file-name" ng-bind="upload.name|fileName">期末大作业</span>
            </span>
            <span class="file-extension" ng-bind="upload.name|fileExtension">.docx</span>
        </div>
    </div>
    <div class="attachment-column attachment-size">
        <span ng-bind="upload.size|humanizeBytes">26.31 KB</span>  <!-- 文件大小 -->
    </div>
    <div class="attachment-column large-7">
        <span class="attachment-operations">
            <a ng-href="/api/uploads/reference/741182/blob"
               ng-click="downloadBlob(activity, upload)"
               target="_blank">
                <i class="font font-table-edit-download"></i>
                <span>下载</span>
            </a>
        </span>
    </div>
</div>
```

**关键选择器**:
| 元素 | 选择器 | 备注 |
|---|---|---|
| 附件行 | `.attachment-row.preview-able` | 每行一个附件 |
| 文件名(name) | `.attachment-row .file-name` | 不含扩展名,如 "期末大作业" |
| 文件扩展名 | `.attachment-row .file-extension` | 含点,如 ".docx" |
| 文件大小 | `.attachment-row .attachment-size` | 人类可读,如 "26.31 KB" |
| 下载链接 | `.attachment-row a[ng-href*="/api/uploads/reference/"]` | API 端点 URL |

**完整文件名** = `.file-name` + `.file-extension`(需拼接)

---

## 3. 下载流程 (HAR entry #156)

**触发**: 用户点击下载链接 → `ng-click="downloadBlob(activity, upload)"` 执行

**HAR 序列**:
```
#155  GET  api/orgs/1/lang-settings       200  (无关,详情页初始化)
#156  GET  media2.szu.edu.cn/download/... 200  (实际下载)
#157  GET  wss://lms.szu.edu.cn/ntf/...   101  (WebSocket,无关)
```

**#156 实际下载 URL**:
```
https://media2.szu.edu.cn/download/file/73c320af540641d7da3769b15a68b6fbedc20008?timestamp=1781715600&token=a06dd4829081bfa3dd07d34ee5c7934e&name=20241130-223949.png
```

**URL 组成部分**:
- **域**: `media2.szu.edu.cn` (CDN,独立于 `lms.szu.edu.cn`)
- **路径**: `/download/file/<40-char-hex-hash>` (40 字符 = SHA-1)
- **`timestamp`**: Unix 时间戳 (如 1781715600)
- **`token`**: 32 字符 hex 鉴权 token
- **`name`**: URL-encoded 原始文件名 (如 `20241130-223949.png`)

**响应头**:
```
Content-Disposition: attachment;filename="20241130-223949.png"
Content-Type: image/png
Content-Encoding: gzip
Content-Length: 12851
```

**鉴权**: **不需要 Cookie**!签名 URL 自带 token,CDN 直接验证 token 后返回文件。

---

## 4. 签名 URL 来源 (未确定)

**已知**: `ng-click="downloadBlob(activity, upload)"` 触发下载。

**未知** (HAR 未捕获):
- `downloadBlob` 是否调 `/api/uploads/reference/741182/blob` 获取签名 URL?
- 签名 URL 何时生成?是详情页加载时预渲染还是点击时实时生成?
- `token` 是 LMS 后端签发还是纯前端计算?

**HAR 缺失线索**:
- 整个 HAR 中**无** `/api/uploads/reference/*` 请求
- #155 → #156 之间无 API 调用
- 说明签名 URL 可能已经在页面初始 HTML 中(被 AngularJS 渲染),或 JS bundle 里有逻辑直接构造

**对 CLI 实现的影响**:
- 我们不知道签名 URL 能否直接从 DOM 提取(`href` 仍是 API 端点,不是 media2 URL)
- 实际下载需要 JS 执行后的 URL,但 headless browser 已经会执行 JS
- **保守方案**: 把 `a[href]` 提取出来,如果以 `media2.szu.edu.cn` 开头就直接用,否则 GET 它并跟随重定向

---

## 5. 与 spec 假设的差异

| Spec 假设 | 实际 | 影响 |
|---|---|---|
| 详情页 URL: `https://lms.szu.edu.cn/...#/<homeworkId>` | `/course/<courseId>/learning-activity#/<homeworkId>?view=scores` | NavigateToHomeworkDetailStep URL 拼接需调整 |
| 附件容器: `.attachment-list` | `.attachment-row.preview-able` | ParseAttachmentsStep 选择器需更新 |
| 附件元素: `.attachment-link` (是 `<a>`) | `.attachment-row` 整行包含下载链接 | 重写为整行解析 |
| 文件名单元素 | 拆为 `.file-name` + `.file-extension` | ParseAttachmentsStep 需拼接 |
| 下载走 `lms.szu.edu.cn` cookie 鉴权 | 走 `media2.szu.edu.cn` 签名 URL (无 cookie) | BrowserLifecycle.downloadAttachment 跨域处理 |

**所有差异已在 spec/plan 中校准。**

---

## 6. 实施要点

### ParseAttachmentsStep 重写

```javascript
(function() {
    var rows = document.querySelectorAll('.attachment-row.preview-able');
    var result = [];
    rows.forEach(function(row) {
        var nameEl = row.querySelector('.file-name');
        var extEl = row.querySelector('.file-extension');
        var sizeEl = row.querySelector('.attachment-size');
        var linkEl = row.querySelector('a[ng-href*="/api/uploads/reference/"]');
        if (!nameEl || !linkEl) return;
        var fileName = (nameEl.textContent.trim() || '') + (extEl ? extEl.textContent.trim() : '');
        result.push({
            fileName: fileName,
            sourceUrl: linkEl.getAttribute('href'),
            fileSizeText: sizeEl ? sizeEl.textContent.trim() : null
        });
    });
    return JSON.stringify(result);
})()
```

### BrowserLifecycle.downloadAttachment 更新

```java
// 接受任意 URL (lms.szu.edu.cn 或 media2.szu.edu.cn)
// - media2: 直接 GET (签名 URL 鉴权)
// - lms: GET 跟随重定向 (Cookie 鉴权 -> 重定向到 media2)
// 都返回字节数
public long downloadAttachment(String url, Path target)
```

### NavigateToHomeworkDetailStep URL 拼接

```java
// 旧假设:
String url = "https://lms.szu.edu.cn/user/index#/" + homeworkId;
// 实际:
String url = "/course/" + courseId + "/learning-activity#/" + homeworkId + "?view=scores";
// 但我们不知道 courseId (从列表页可获取,需扩展)
```

**简化**: 直接 navigate 到 `https://lms.szu.edu.cn/user/index#/<homeworkId>` 也能进入详情页(AngularJS hash 路由),courseId 可选。
