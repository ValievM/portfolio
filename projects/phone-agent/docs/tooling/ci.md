# CI и запуск Gradle

## CI на GitHub
`.github/workflows/check.yml`: на каждый push в `main` и `dev` и на каждый pull request выполняет `./gradlew check` (сборка, spotless, detekt, lint, unit- и UI-тесты в Robolectric) и `python3 scripts/check_docs.py`. Права токена только на чтение.

`scripts/check.sh` в CI не используется: в нём жёстко прописан путь к JDK 17 в Homebrew (macOS). На runner'е JDK 17 ставит `actions/setup-java`, Android SDK уже есть в образе `ubuntu-latest` (шаг `android-actions/setup-android` не используем: на новом образе он падает на пакете `tools`), принимаются только лицензии.

**Статус: зелёный** (запуск 36139162430 на `c4df4b4`, 2026-09-25, 9 мин 44 с). Что чинили по пути: `setup-android` падал на пакете `tools` (заменён шагом лицензий, он сам находит `sdkmanager`: папка `latest` или номер версии); тест ленты зависел от скорости машины (фоновый разбор markdown), исправлен; форматтер случайно падал в гонке с KSP (не обходит `build`). Действия подключены по мажорным тегам (`@v4`), закрепление по SHA на будущее.

На заметку из лога: `setup-java@v4` устарел (перейти на `@v5`); метка `ubuntu-latest` с 2026-10-19 переедет на Ubuntu 26, при сбое закрепить `ubuntu-24.04`.

## JDK для Gradle
Gradle 8.13 не умеет работать на JDK 24 (системная в этой среде), а проекту нужна 17. Файл `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=17`) заставляет демон Gradle запускаться на JDK 17. Чтобы Gradle нашёл её на Mac, в `~/.gradle/gradle.properties` должна быть строка `org.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (это локальная настройка, в репозиторий не входит). После этого обычный `./gradlew` работает без `JAVA_HOME` и без флагов; `scripts/check.sh` по-прежнему годится.

## Пуш
Пушит агент только по слову владельца («пушь»). Перед пушем проверка на секреты (`*.jks`, `local.properties`, `spike/auth*.json`, токены, ключи в диффе); вся история на GitHub проверена 2026-09-25: секретов нет.
