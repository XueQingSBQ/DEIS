@echo off
setlocal
chcp 65001 >nul

set "BASE_DIR=%~dp0"
for %%I in ("%BASE_DIR%..\..") do set "APP_HOME=%%~fI"
set "JAR_PATH=%APP_HOME%\target\dris-enterprise-backend-v2-0.1.0.jar"
set "NODE_DIR=%BASE_DIR%nodes"
set "LOG_DIR=%BASE_DIR%runtime\logs"
set "JAVA_EXE=C:\Program Files\Java\jdk-1.8\bin\java.exe"
set "JAVA_OPTS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

call :startNode node1-country
call :startNode node2-province
call :startNode node3-city-a
call :startNode node4-county-a

exit /b 0

:startNode
set "NODE_NAME=%~1"
set "CFG_PATH=%NODE_DIR%\%NODE_NAME%.yml"
set "OUT_LOG=%LOG_DIR%\%NODE_NAME%.out.log"
set "ERR_LOG=%LOG_DIR%\%NODE_NAME%.err.log"
start "" /b "%JAVA_EXE%" %JAVA_OPTS% -jar "%JAR_PATH%" "--spring.config.additional-location=file:///%CFG_PATH:\=/%" 1>"%OUT_LOG%" 2>"%ERR_LOG%"
exit /b 0
