# Аудит логов (этап 5.5)

> См. также:  · [manual-verification.md](manual-verification.md)

Цель: в логах нет токенов, текста чата и данных файлов (имён, путей, содержимого). Проверено 23.09.2026.

## Как устроено
Весь код пишет только через `AppLogger` (`android.util.Log` запрещён правилом `ArchitectureTest`). В приложении логгер один: `RedactingAppLogger(AndroidLogcatLogger)` — сначала вычищает токены из сообщения и значений `fields`, у исключения отбрасывает сообщение и всю цепочку причин, оставляя тип и стек (`LogRedaction.sanitizedThrowable`), потом пишет в Logcat и в файл `noBackupFilesDir/app.log` (последние 64 КБ, стирается при удалении приложения, в бэкап не попадает). `AndroidLogcatLogger` повторно санитизирует исключение на входе в оба sink-а — защита работает даже если его когда-нибудь забиндят без декоратора. Сбой записи файла проглатывается: Logcat уже получил строку, лог не должен ронять приложение. Вычистка распознаёт только формы токенов, поэтому главное правило другое: **сообщение лога — константа, всё динамическое идёт в `fields`, и в `fields` не кладём ничего, что пришло от пользователя, модели, файла или сервера как текст.**

## Что пишется (28 мест)
| Где | Что попадает в лог |
|---|---|
| `ToolPipeline` | имя инструмента и исход (`success`/`failed`/`rejected`), без аргументов и результата |
| `ResponsesGateway` | сетевой сбой (исключение без текста), отказ модели: HTTP-статус и `type`/`code`/`param` из ошибки (`summarizeErrorBody`); тела ответа нет |
| `SessionManager`, `ChatGptBrowserAuth`, `OkHttpTokenRefreshGateway`, `EncryptedTokenStore` | способ входа, тип сбоя, HTTP-статус, OAuth-код ошибки (`invalid_grant`), номер попытки; токенов и тел ответов нет |
| `AlarmIntentLauncher` | отказ системы запустить будильник (исключение без текста) |
| `CrashLoggingHandler` | имя потока и исключение (стек, без текста сообщения) |

## Что нашли и исправили
1. **Тело ответа при отказе модели.** `ResponsesGateway` писал первые 500 символов тела ответа. Ошибка может повторять кусок запроса (текст сообщения, имя файла), а `RedactingAppLogger` вычищает только токены, и `app.log` живёт дольше чата. Теперь в лог идёт `summarizeErrorBody`: только значения `type`, `code`, `param`, похожие на идентификаторы, либо вид тела (`non-JSON body of N chars`). Тесты: `ErrorBodySummaryTest`, `ResponsesGatewayTest` («a rejected request logs the error identifiers but never the response body»).
2. **Текст в сообщении лога.** `CrashLoggingHandler` подставлял имя потока прямо в сообщение. Данные там были безобидные, но правило «сообщение — константа» нельзя держать с исключениями, поэтому имя потока перенесено в `fields`, а правило закреплено тестом `ArchitectureTest` («log messages are constants and data goes through fields»).
3. **Произвольные ключи JSON в `summarizeErrorBody`.** Ветка `fields=` печатала имена ключей верхнего уровня тела ошибки — а сервер может echoed back имя файла в самом ключе (например `private_contract.pdf`). Теперь ключи не печатаются вообще, только их количество (`json object, top-level keys: N`); тела длиннее 8 КБ не парсятся, а описываются размером (`body of N chars`). Тесты: `ErrorBodySummaryTest` («a top-level key that looks like a file name is never echoed», «an oversized body is reported by size alone without being parsed»).
4. **Текст исключения в sink-ах.** `RedactingAppLogger` вычищал сообщение исключения регексами — но произвольный текст пользователя в сообщении регекс не распознает, а цепочка причин не трогалась вовсе. Теперь сообщение и причины отбрасываются целиком, остаются тип и стек; `AndroidLogcatLogger` санитизирует ещё раз на входе (в файл пишется только тип). Тесты: `RedactingAppLoggerTest` («an exception message and cause never reach the sink»), `AndroidLogcatLoggerTest`.
5. **Надёжность записи.** Сбой записи `app.log` (диск заполнен, путь недоступен) ронял приложение — теперь проглатывается, Logcat строку уже получил. `CrashLoggingHandler` не вызывал предыдущий обработчик, если сам логгер упал — теперь вызывает всегда (try/catch/finally). Тесты: `AndroidLogcatLoggerTest` («a failed file write never breaks logging»), `CrashLoggingHandlerTest` («the previous handler always runs even when the logger itself throws»).

## Остаётся в силе
- Стек исключения пишется в Logcat целиком (классы и строки кода, не данные); в файл `app.log` — только тип исключения.
- В `fields` разрешены только идентификаторы, коды, счётчики и имена из нашего кода. Новое поле с текстом от пользователя, модели или файла — нарушение; на ревью смотреть все `fields =`.

## Как перепроверить
```
grep -rnE "\.(debug|info|warn|error)\(" --include=*.kt core data tools app/src/main
```
Каждое найденное место: сообщение — константа, `fields` — только из списка выше. На телефоне: сделать диалог с инструментами и просмотреть `adb logcat` и `adb shell run-as com.example.phoneagent cat no_backup/app.log` (пункт в [manual-verification.md](manual-verification.md)).
