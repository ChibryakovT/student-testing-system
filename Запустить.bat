@echo off
rem Двойной щелчок по этому файлу запускает приложение и открывает браузер.
rem Чтобы остановить приложение, закройте это окно.
chcp 65001 >nul
cd /d "%~dp0"
title Электронная система тестирования
echo ==============================================
echo   Электронная система тестирования студентов
echo ==============================================

where java >nul 2>nul
if errorlevel 1 (
    echo ОШИБКА: не найдена Java 17 или новее.
    echo Установите JDK 21: https://adoptium.net/ и перезапустите этот файл.
    pause
    exit /b 1
)

if not exist ".env" (
    echo ОШИБКА: нет файла .env с настройками.
    echo Скопируйте .env.example в .env и заполните пароли ^(см. README.md^).
    pause
    exit /b 1
)

powershell -NoProfile -Command "if (Test-NetConnection localhost -Port 8080 -InformationLevel Quiet -WarningAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
if not errorlevel 1 (
    echo Приложение уже запущено. Открываю браузер...
    start "" http://localhost:8080
    exit /b 0
)

rem Когда сервер поднимется - открыть браузер
start "" /min powershell -NoProfile -WindowStyle Hidden -Command "for ($i = 0; $i -lt 120; $i++) { try { Invoke-WebRequest http://localhost:8080/login -UseBasicParsing -TimeoutSec 2 | Out-Null; Start-Process http://localhost:8080; break } catch { Start-Sleep -Seconds 2 } }"

echo Запуск... Первый запуск может занять пару минут ^(скачиваются библиотеки^).
echo Браузер откроется автоматически. Чтобы остановить приложение - закройте это окно.
echo.
where mvn >nul 2>nul
if not errorlevel 1 (
    call mvn -q spring-boot:run
) else (
    call mvnw.cmd -q spring-boot:run
)
pause
