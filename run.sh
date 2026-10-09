#!/bin/bash
# Запуск приложения: ./run.sh  → затем откройте http://localhost:8080
cd "$(dirname "$0")"

# Ищем Java 17+ (Spring Boot 3 не работает на более старых версиях)
for candidate in "$JAVA_HOME" /opt/homebrew/opt/openjdk@21 /opt/homebrew/opt/openjdk@17 "$(/usr/libexec/java_home -v 17+ 2>/dev/null)"; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
        export JAVA_HOME="$candidate"
        break
    fi
done
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "Не найдена Java 17+. Установите: brew install openjdk@21"
    exit 1
fi
if [ ! -f .env ]; then
    echo "Нет файла .env. Скопируйте .env.example в .env и заполните пароли (см. README)."
    exit 1
fi

echo "Java: $JAVA_HOME"
echo "Запуск... когда появится строка 'Started TestingSystemApplication', откройте http://localhost:8080"
exec mvn -q spring-boot:run
