#!/usr/bin/env python3
"""
Template external skill: echo back stdin args.
Usage: python3 run.py <skillName>
"""
import json
import sys


def main():
    skill_name = sys.argv[1] if len(sys.argv) > 1 else "template_echo"
    raw = sys.stdin.read().strip()
    if not raw:
        raw = "{}"
    try:
        args = json.loads(raw)
    except json.JSONDecodeError:
        args = {}

    message = args.get("message", "hello")
    print(json.dumps({
        "success": True,
        "data": {
            "skill": skill_name,
            "echo": message,
            "receivedArgs": args
        }
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
