@echo off
set MAVEN_OPTS=
setlocal
cd /d "%~dp0"
call mvn %*