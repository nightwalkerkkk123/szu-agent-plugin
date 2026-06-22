@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
set "SKILL_NAME=%~1"
set "SCRIPT_DIR=%~dp0"
python "!SCRIPT_DIR!run.py" !SKILL_NAME!
