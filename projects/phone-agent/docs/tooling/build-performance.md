# Оптимизация сборки

> См. также: [tooling/context7.md](context7.md)

Статус: **не применено**, только замечания на будущее (зафиксированы 22.09.2026 по просьбе пользователя). Машина для сборки: 10 ядер, 16 ГБ RAM.

## Быстрые и безопасные правки в `gradle.properties`

- **Build cache выключен.** `org.gradle.caching=true` не установлен — Gradle не переиспользует результаты задач между сборками (например после переключения веток или `git stash`). Особенно полезно для `detekt` и `test`, которые поддерживают кеширование из коробки. Цена: кеш занимает место на диске (обычно сотни МБ — пара ГБ), эффект виден не на первой сборке, а на повторных.
- **Heap демона занижен.** Сейчас `-Xmx3g` при 16 ГБ доступной памяти. Для проекта с Compose + Hilt (KSP) + 5 модулей стоит поднять до `-Xmx4g` или `-Xmx6g` и явно задать `-XX:MaxMetaspaceSize=1g`, чтобы реже упираться в GC во время компиляции.
- **Kotlin-демон не закреплён отдельно.** В логах сборки мелькает `Detected multiple Kotlin daemon sessions` — Android Studio и CLI-вызовы `./gradlew` поднимают разные Kotlin-демоны вместо одного переиспользуемого, потому что их JVM-аргументы для демона не совпадают. Фикс: явно задать `kotlin.daemon.jvmargs` в `gradle.properties`, чтобы оба пути запуска сходились на одном демоне.
- **Unit-тесты не распараллелены внутри модуля.** В конвенциях (`build-logic/src/main/kotlin/phoneagent.*.gradle.kts`) `tasks.withType<Test>` не задаёт `maxParallelForks` — тесты одного модуля идут последовательно в один JVM-процесс. Можно добавить `maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)`.

## Осознанно не трогаем

- **KSP1 вместо KSP2.** KSP1 выбрали, потому что KSP2 ломал импорт проекта в Kotlin LSP (Serena). Serena удалена 2026-09-26, причины больше нет: KSP2 быстрее, стоит попробовать отдельной задачей с полным `check` (Hilt, Room).
- **`org.gradle.configureondemand`** — при 5 модулях выигрыш незаметен, а риск рассинхронизации конфигурации с configuration cache есть. Не включаем.

## Как проверять эффект

Замер до/после одной и той же команды с `--stop` перед первым (холодным) запуском:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --stop
time JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew check -Dorg.gradle.java.installations.paths=$JAVA_HOME
```

Второй (тёплый, с build cache) запуск той же командой без `clean` должен быть заметно быстрее за счёт переиспользованных задач `detekt`/`test`.
