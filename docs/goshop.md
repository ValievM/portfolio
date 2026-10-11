# GoShop · Go

REST API интернет-магазина: каталог, пользователи, корзина, заказы и отзывы. Google OAuth2 / JWT, PostgreSQL, Redis и S3-совместимое файловое хранилище.

## Маршрут ревью

[Домен и порты](../projects/goshop/internal/core/) → [HTTP, БД и хранилище](../projects/goshop/internal/adapters/) → [конфигурация](../projects/goshop/internal/config/config.go) → [OpenAPI](../projects/goshop/docs/swagger/swagger.yaml).

## Запуск

Требуются Go версии из `go.mod`, Docker Compose и CLI `migrate` для миграций. Конфигурация приложения читается из `config.yaml`; переменные `.env` нужны Docker Compose.

```bash
cd projects/goshop
cp .env.example .env
cp config.example.yaml config.yaml
docker compose up -d postgres redis minio
```

В `config.yaml` укажите реальные локальные настройки БД, Redis и MinIO; они должны совпадать с `.env`. Замените JWT-заглушку случайным секретом длиной минимум 32 символа. Создайте в MinIO бакет `goshop` (консоль: http://localhost:9001).

```bash
migrate -path ./migrations -database 'postgres://goshop_user:your_secure_password_here@localhost:5432/goshop_db?sslmode=disable' up
go run ./cmd/goshop
```

При изменении имени пользователя, пароля или порта скорректируйте строку миграций. Для Google OAuth задайте собственные client ID, client secret и redirect URL в конфигурации.

```bash
go build ./...
go vet ./...
```

В импортированной версии нет Go-файлов тестов. Заявление о снижении нагрузки на PostgreSQL на 40% не включено в описание: в проекте нет отчёта, подтверждающего эту цифру.

Исходный [README](../projects/goshop/README.md) содержит устаревшие команды; для запуска используйте эту страницу. [Исходный репозиторий](https://github.com/ValievM/goshop).
