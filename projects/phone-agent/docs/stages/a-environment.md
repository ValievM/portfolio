# Этап A. Окружение и инструменты

> Читать перед началом: [Context7](../tooling/context7.md). Serena удалена 2026-09-26, пункты A.5-A.7 про неё историчны.

**Цель:** чтобы дальше не ловить проблемы установки.

**Статус:** закрыт; A.2 и A.7 отложены по решению владельца проекта.

- [x] A.1 Установлен Android Studio; через SDK Manager стоят актуальная Android SDK Platform, Build-Tools, Platform-Tools (`adb`).
- [x] A.2 Телефон: включён режим разработчика и отладка по USB (или создан эмулятор). **Отложено:** проверка устройства; этап 1 выполняется на эмуляторе.
- [x] A.3 Установлен `git`; создан репозиторий; `.gitignore` для Android + `local.properties`, `*.jks`, `*.keystore`, `spike/auth*.json`, `.serena/cache/`.
- [x] A.4 Установлен Codex CLI, выполнен вход, `codex` запускается в папке проекта.
- [x] A.5 Установлен `uv`; Serena: `uv tool install -p 3.13 serena-agent`, затем `serena init`.
- [x] A.6 В `~/.codex/config.toml` добавлены Serena и Context7 (блок ниже).
- [x] A.7 Ключ Context7 (бесплатный, с context7.com/dashboard) лежит в переменной окружения `CONTEXT7_API_KEY`, не в репозитории. **Отложено:** ключ не задан; до его добавления работаем без Context7.
- [x] A.8 В корне репозитория лежат `AGENTS.md`, `docs/` и `scripts/check_docs.py`.

Блок для `~/.codex/config.toml`:
```toml
[mcp_servers.serena]
startup_timeout_sec = 15
command = "serena"
args = ["start-mcp-server", "--project-from-cwd", "--context=codex"]

[mcp_servers.context7]
url = "https://mcp.context7.com/mcp"
bearer_token_env_var = "CONTEXT7_API_KEY"
```
Запасной вариант для Context7, если удалённый сервер не подключается: `codex mcp add context7 -- npx -y @upstash/context7-mcp` (нужен Node.js).
Serena запускать из корня проекта (`--project-from-cwd` берёт текущую папку).

**Что должно быть по итогу:** репозиторий с первым коммитом (`AGENTS.md`, `docs/`), рабочий Codex с двумя MCP-серверами.

**Как проверить:**
- `adb devices` показывает телефон/эмулятор.
- `codex mcp list` показывает `serena` и `context7`.
- В Codex: «покажи список инструментов Serena» → перечисляет `find_symbol`, `get_symbols_overview` и т.д.
- В Codex: «use context7: как хранить данные в Jetpack DataStore» → ответ со ссылкой на документацию.
- В Codex: «какие правила проекта ты загрузил из AGENTS.md?» → пересказывает разделы.

**Ворота:** все три проверки пройдены.

**Стартовый промпт:** «Прочитай AGENTS.md. Помоги пройти этап A из этого файла по шагам, после каждого шага жди моего подтверждения.»

---

Следующий этап: [0-spike.md](0-spike.md). Возврат к списку: [README.md](README.md).
