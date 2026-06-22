#!/usr/bin/env python3
"""
外部 Skill 示例：多语言问候。

调用方式：
    python3 run.py example_greet '{"name":"Alice","language":"en"}'

返回统一 JSON 信封：
    {"success": true, "data": {...}}
    或
    {"success": false, "errorCode": "...", "errorMessage": "..."}
"""
import json
import sys


def main():
    if len(sys.argv) < 2:
        print(json.dumps({
            "success": False,
            "errorCode": "INVALID_REQUEST",
            "errorMessage": "用法: run.py <skillName> (args JSON from stdin)"
        }))
        sys.exit(1)

    skill_name = sys.argv[1]
    raw = sys.stdin.read().strip()
    if not raw:
        raw = "{}"
    try:
        args = json.loads(raw)
    except json.JSONDecodeError as e:
        print(json.dumps({
            "success": False,
            "errorCode": "INVALID_REQUEST",
            "errorMessage": f"参数 JSON 解析失败: {e}"
        }))
        sys.exit(1)

    name = args.get("name")
    if not name:
        print(json.dumps({
            "success": False,
            "errorCode": "INVALID_REQUEST",
            "errorMessage": "缺少必填参数: name"
        }))
        sys.exit(1)

    language = args.get("language", "zh")
    greetings = {
        "zh": f"你好，{name}！",
        "en": f"Hello, {name}!",
        "jp": f"こんにちは、{name}さん！",
    }
    message = greetings.get(language, greetings["zh"])

    print(json.dumps({
        "success": True,
        "data": {
            "skill": skill_name,
            "greeting": message,
            "language": language
        }
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
