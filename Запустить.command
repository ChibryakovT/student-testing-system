#!/bin/bash
# Двойной щелчок по этому файлу в Finder запускает приложение и открывает браузер.
# Чтобы остановить приложение, закройте окно Терминала.
cd "$(dirname "$0")"
clear
echo "=============================================="
echo "  Электронная система тестирования студентов"
echo "=============================================="

# 1. Java 17+
for candidate in "$JAVA_HOME" /opt/homebrew/opt/openjdk@21 /opt/homebrew/opt/openjdk@17 "$(/usr/libexec/java_home -v 17+ 2>/dev/null)"; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
done
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "ОШИБКА: не найдена Java 17 или новее. Установите её командой: brew install openjdk@21"
    read -n 1 -s -r -p "Нажмите любую клавишу, чтобы закрыть окно"; exit 1
fi

# 2. Настройки
if [ ! -f .env ]; then
    echo "ОШИБКА: нет файла .env с настройками. Скопируйте .env.example в .env и заполните пароли (см. README.md)."
    read -n 1 -s -r -p "Нажмите любую клавишу, чтобы закрыть окно"; exit 1
fi

# 3. PostgreSQL: если установлен Postgres.app и сервер не отвечает — запускаем его
if ! nc -z localhost 5432 2>/dev/null && [ -d /Applications/Postgres.app ]; then
    echo "Запускаю PostgreSQL (Postgres.app)..."
    open -g -a Postgres
    for i in $(seq 1 30); do nc -z localhost 5432 2>/dev/null && break; sleep 1; done
fi
if ! nc -z localhost 5432 2>/dev/null; then
    echo "ОШИБКА: PostgreSQL не запущен (порт 5432). Запустите сервер базы данных и повторите."
    read -n 1 -s -r -p "Нажмите любую клавишу, чтобы закрыть окно"; exit 1
fi

# 4. Порт 8080 уже занят — значит, приложение уже работает
if nc -z localhost 8080 2>/dev/null; then
    echo "Приложение уже запущено. Открываю браузер..."
    open http://localhost:8080
    sleep 2; exit 0
fi

# 5. Когда сервер поднимется — открыть браузер
( for i in $(seq 1 120); do
      if curl -s -o /dev/null http://localhost:8080/login; then open http://localhost:8080; break; fi
      sleep 2
  done ) &

echo "Запуск... Первый запуск может занять пару минут (скачиваются библиотеки)."
echo "Браузер откроется автоматически. Чтобы остановить приложение — закройте это окно."
echo
if command -v mvn >/dev/null; then MVN=mvn; elif [ -x ./mvnw ]; then MVN=./mvnw; else
    echo "ОШИБКА: не найден Maven. Установите его командой: brew install maven"
    read -n 1 -s -r -p "Нажмите любую клавишу, чтобы закрыть окно"; exit 1
fi
$MVN -q spring-boot:run
