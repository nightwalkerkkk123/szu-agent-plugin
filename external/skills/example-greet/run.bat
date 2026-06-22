@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
python "!SCRIPT_DIR!run.py" %1
