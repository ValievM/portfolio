#!/usr/bin/env bash
# Локальный pipeline проверок для phone-agent.
# Использование:
#   scripts/check.sh            — быстрая проверка (detekt + unit-тесты + docs)
#   scripts/check.sh full       — полный ./gradlew check + docs
#   scripts/check.sh detekt     — только detekt
#   scripts/check.sh test       — только unit-тесты всех модулей
#   scripts/check.sh docs       — только проверка документации
#   scripts/check.sh instrumented — androidTest (нужен подключённый девайс/эмулятор)
#
# Всегда запускать с JDK 17 явно — этот скрипт делает это сам,
# сборка ломается на JDK 21/24 (см. docs/tooling).

set -euo pipefail
cd "$(dirname "$0")/.."

JAVA17_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
if [[ ! -d "$JAVA17_HOME" ]]; then
    echo "JDK 17 не найден по пути $JAVA17_HOME. Установи: brew install openjdk@17" >&2
    exit 1
fi
export JAVA_HOME="$JAVA17_HOME"
GRADLE_OPTS=(-Dorg.gradle.java.installations.paths="$JAVA_HOME")

run_gradle() {
    echo "==> ./gradlew $* (JDK 17)"
    ./gradlew "$@" "${GRADLE_OPTS[@]}"
}

run_docs() {
    echo "==> python3 scripts/check_docs.py"
    python3 scripts/check_docs.py
}

case "${1:-quick}" in
    quick)
        run_gradle detekt test
        run_docs
        ;;
    full)
        run_gradle check
        run_docs
        ;;
    detekt)
        run_gradle detekt
        ;;
    test)
        run_gradle test
        ;;
    docs)
        run_docs
        ;;
    instrumented)
        run_gradle connectedAndroidTest
        ;;
    *)
        echo "Неизвестный режим: $1" >&2
        echo "Доступно: quick (default), full, detekt, test, docs, instrumented" >&2
        exit 1
        ;;
esac

echo "==> OK"
