# kb_query

查询深大校园知识库(校园基础、餐饮、图书馆、学业、FAQ 五大类),用关键词匹配返回相关条目。
重要约束(必须遵守,否则调用会失败或返回空):
1. query 是必填,支持中文/英文混合关键词,例如 "图书馆"、"食堂"、"选课"、"校园卡"。
   多个关键词用空格或半角逗号分隔,例如 "图书馆 开馆时间"。
2. 匹配策略: 优先 Exact(整词命中),回退 Contains(子串),回退 Regex(默认关闭)。
   简而言之 query="图书馆" 会精确命中所有含"图书馆"的条目,不必担心歧义。
3. category 可选,枚举值固定 5 个: CAMPUS_BASICS(校园基础) / DINING(餐饮服务) /
   LIBRARY(图书馆) / ACADEMICS(学业选课) / FAQ(常见问题)。传错(如小写 "library")会抛
   IllegalArgumentException。LLM 必须严格使用大写下划线形式。
4. limit 可选,默认 5。传 0 或负数抛 IllegalArgumentException("limit must be positive")。
5. 返回顺序按相关度(score)降序,score 范围 0.0-1.0。不保证覆盖所有匹配项,若用户需要
   全部结果应传 limit=50 并提示用户进一步筛选。
6. 这是纯静态查询,不需要凭证、不需要浏览器、不会发起任何 IO。
7. 适合回答"图书馆几点关门?"、"食堂可以用支付宝吗?"、"选课什么时候开始?"等问题。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `query` | 是 | `string` | 查询关键词,中文或英文,例如 '图书馆'、'食堂'、'选课'。必填。 | examples=[图书馆, 食堂, 选课 退课] |
| `limit` | 否 | `integer` | 最大返回条数,默认 5。必须 > 0。 | default=5; minimum=1; examples=[5, 10] |
| `category` | 否 | `string` | 可选分类过滤,默认不过滤。 | enum=[CAMPUS_BASICS, DINING, LIBRARY, ACADEMICS, FAQ]; examples=[LIBRARY] |

## 枚举

| 参数 | 可选值 |
|---|---|
| `category` | `CAMPUS_BASICS`, `DINING`, `LIBRARY`, `ACADEMICS`, `FAQ` |

## 示例

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "图书馆"
  }
}
```

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "食堂",
    "category": "DINING"
  }
}
```

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "选课",
    "limit": 10
  }
}
```

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "校园卡",
    "category": "CAMPUS_BASICS"
  }
}
```

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "图书馆 开馆时间",
    "category": "LIBRARY",
    "limit": 3
  }
}
```

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "FAQ 校园网"
  }
}
```

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "ACADEMICS 选课",
    "category": "ACADEMICS",
    "limit": 20
  }
}
```

## 返回值

```text
List<KnowledgeResult>:
- snippet: 命中的正文片段
- sourcePath: classpath 内源 Markdown 路径,例如 "knowledge/03-library.md"
- relevanceScore: 1.0=标题精确命中,0.9=正文精确命中,0.7=子串命中,0.5=正则命中
```

## 常见错误

- category 传 "library"(小写)→ INVALID_REQUEST;必须用大写 "LIBRARY"
- query=""(空字符串)→ INVALID_REQUEST("query" missing/blank);必须非空
- limit=0 或负数 → INVALID_REQUEST("limit must be positive")

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `kb_query`

