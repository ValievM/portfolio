# Визуальный стиль: цвет, шрифты, формы

> См. также: [обзор](overview.md) · [иконки, движение, тексты](icons-motion-copy.md) · [компоненты](components.md)

## Цвет
- **Фирменная палитра «Стык»** по умолчанию: [brand.md](brand.md), значения в `brand/joint-tokens.json`, в коде `BrandColorSchemes.kt` (файл генерируется из JSON, `BrandThemeTokensTest` ловит расхождение).
- **«Цвета системы»** (динамические цвета Android 12+) отдельная настройка, по умолчанию выключена; тема «Как в системе / Светлая / Тёмная» тоже в настройках и применяется сразу ко всему приложению.
- **Тёмная и светлая темы** по системе; обе обязательны, обе в превью.
- **Статусные цвета** (успех, предупреждение): в M3 их нет: значения из `brand/joint-tokens.json`, в коде `AppExtendedColors` (`success`, `warning`, их `on…` и `brandMark`) через `CompositionLocal`, в тёмной теме и при цветах системы они меняются вместе с палитрой.
- Слои — через роли `surface` и `surfaceContainer*`, не через свои серые.

| Элемент | Фон | Контент |
|---|---|---|
| Экран | `surface` | `onSurface` |
| Сообщение пользователя | `secondaryContainer` | `onSecondaryContainer` |
| Сообщение ассистента | без фона (на `surface`) | `onSurface` |
| Карточка инструмента | `surfaceContainer`, без рамки, статус плашкой ниже | `onSurface`, второстепенное `onSurfaceVariant` |
| Панель ввода | `surfaceContainerLow` на всю ширину | |
| Поле ввода | `surfaceContainerHigh`, рамка `outline` 1dp | `onSurface` |
| Шторка подтверждения | `surfaceContainerLow` (по умолчанию M3) | `onSurface` |
| Успех, ожидание | `successContainer`, `warningContainer` (custom) | `onSuccessContainer`, `onWarningContainer` |
| Ошибка, баннер «нет сети», «сессия истекла» | `errorContainer` + иконка | `onErrorContainer` |

## Типографика
- Шрифт интерфейса: **Inter** (variable, вшит; решение «Стыка»), шкала и веса в [brand.md](brand.md). Кириллица проверена строкой «Ёё Щщ Ъъ Ыы».
- Шрифт кода и аргументов инструментов: **JetBrains Mono**.
- Шрифты **вшиваем** в APK (`res/font`, variable). Скачиваемые Google Fonts не подходят: variable-шрифты через них не поддерживаются, плюс нужна сеть и Google-сервисы. Variable-шрифты в Compose работают с Android 8, а у нас minSdk 26.
- Лицензии шрифтов (OFL) и иконок (Apache 2.0): тексты сохраняем и показываем на экране «О приложении» (этап 6).

| Где | Стиль M3 | Замечание |
|---|---|---|
| Текст сообщений | `bodyLarge` (16/24) | для чтения не меньше 16sp |
| Заголовки в markdown | h1 `titleLarge`, h2 `titleMedium`, h3 `titleSmall` | не крупнее `titleLarge`, чтобы ответы «не кричали» |
| Название инструмента в карточке | `titleSmall`, суть `bodyLarge` | |
| Статус | `labelLarge` (14sp, важное не меньше 14) | |
| Код и аргументы | `AppCodeTextStyle` (моно 14/22) | горизонтальный скролл внутри блока |
| Кнопки | `labelLarge`, weight 600 | |
| Заголовок шторки | `titleLarge` | |
| Подсказки, ошибки поля | `bodySmall` | |

Размеры только в `sp`, фиксированной высоты у текстовых контейнеров нет, проверка при масштабе шрифта 200%.

## Формы и глубина
- Формы из токенов `AppTheme.shapeTokens` (цифры в [brand.md](brand.md)); кнопки только через `PrimaryButton`/`SecondaryButton`/`QuietButton`, у M3 нет роли формы кнопки.
- Глубина через тональные слои, тени почти не используем.
