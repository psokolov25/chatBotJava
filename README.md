# Telegram Orchestra Bot — Java 17 + Micronaut

Реализация исходного Python-проекта `Queue Telegram Bot (Orchestra + CometD)` на стеке **Java 17 + Micronaut 4.10.13**.

## Что перенесено

- Telegram Bot API через long polling, без webhook.
- Многофилиальная конфигурация через `ORCHESTRA_BRANCHES`.
- Single-branch fallback через `BRANCH_ID`, `ORCHESTRA_ENTRY_POINT_ID`, `ORCHESTRA_BRANCH_CODE`, `ORCHESTRA_BRANCH_NAME`.
- Выбор отделения и услуги в Telegram inline keyboard.
- Клиентский путь из `client_path.yml` с переходами `next_question_id`.
- Маппинг ответов на услуги через `services` или `service_names`.
- Режимы мультисервиса: `auto`, `choose`, `choose_many`.
- Создание визита в Orchestra REST API.
- Создание визита в Axioma REST API.
- Подписка на Orchestra CometD `/events/{prefix}/QVoiceLight`.
- Обработка событий `VISIT_CALL` / `VISIT_RECALL` и персональная отправка уведомления пользователю.
- Нормализация Axioma Kafka событий `VISIT_CALLED` / `VISIT_RECALLED`.
- Шаблоны уведомлений с безопасным сохранением неизвестных плейсхолдеров.
- Маскирование персональных данных в логах.
- Служебный endpoint `/api/bot/status`.

## Требования

- Java 17+
- Maven 3.9+
- Доступ к Telegram Bot API
- Доступ к Orchestra REST + CometD и/или Axioma REST + Kafka

## Сборка

```bash
mvn -U clean test package
```

> `-U` нужен после неудачной попытки с несуществующим `io.micronaut.platform:micronaut-platform:4.9.10`, потому что Maven кэширует отрицательный результат разрешения артефакта в локальном репозитории.

Запуск:

```bash
java -jar target/telegram-orchestra-bot-1.0.0-SNAPSHOT.jar
```

Отладочные значения из исходного `.env` уже перенесены в два места: как дефолты в `src/main/resources/application.yml` и как значения в корневой `.env`, который подключён в `docker-compose.yml` через `env_file`. Поэтому проект можно запускать сразу для отладки, а при необходимости менять параметры в `.env` без правки Java-кода.

Проверка статуса:

```bash
curl http://localhost:8080/api/bot/status
```

## Docker Compose

```bash
docker compose up -d --build
docker compose logs -f telegram-orchestra-bot
```

## Основная конфигурация

Основная конфигурация хранится в `src/main/resources/application.yml`. В нём используются placeholders вида `${ENV_NAME:debug-default}`: если переменная окружения задана, Micronaut берёт её; если нет — использует отладочный дефолт из исходного Python `.env`. Для Docker Compose эти же значения продублированы в корневом `.env`, подключённом через `env_file`. Ключевые секции:

| Секция `application.yml` | Назначение |
|---|---|
| `bot.telegram` | Telegram Bot API token, URL и long polling |
| `bot.queue` | Orchestra/Axioma URL, логины, пароли, fallback-филиал и JSON филиалов |
| `bot.runtime` | клиентский путь, порядок диалога, blacklist услуг, шаблоны уведомлений, multi-service overrides |
| `bot.cometd` | настройки Orchestra CometD подписки |
| `bot.kafka` | настройки Kafka consumer для Axioma |

Пример `ORCHESTRA_BRANCHES`:

```json
[
  {"id":"6","name":"Нотариус","prefix":"NTR","entry_point_id":"2","queue_system":"orchestra","base_url":"http://192.168.0.38:8080/","login":"superadmin","password":"secret"},
  {"id":"cd842979-3dc1-4505-a1ae-9a92f0622da2","name":"Банк Дубна","prefix":"DUB","entry_point_id":"f7ff91a7-a2a7-4b90-adaf-b2d38a24e0f2","queue_system":"axioma","base_url":"http://192.168.8.40:8080/","login":"superadmin","password":"secret"}
]
```

## REST-точки СУО

### Orchestra

Получение услуг:

```text
GET /rest/servicepoint/branches/{branchId}/services/
```

Создание визита:

```text
POST /rest/entrypoint/branches/{branchId}/entryPoints/{entryPointId}/visits/
```

Тело:

```json
{
  "services": ["101"],
  "parameters": {
    "TelegramCustomerId": "123456",
    "TelegramChatId": "123456",
    "TelegramCustomerFullName": "Иван Иванов",
    "TelegramClientPath": "{...}"
  }
}
```

### Axioma

Получение услуг:

```text
GET /entrypoint/branches/{branchId}/services
```

Создание визита:

```text
POST /entrypoint/branches/{branchId}/entry-points/{entryPointId}/visits/parameters?printTicket=false
```

Тело:

```json
{
  "serviceIds": ["101"],
  "parameters": {
    "TelegramCustomerId": "123456",
    "TelegramChatId": "123456",
    "TelegramCustomerFullName": "Иван Иванов"
  }
}
```

## Важные отличия от Python-версии

- Для отладки значения из исходного `.env` перенесены как дефолты в `application.yml` и продублированы в корневом `.env`, который использует `docker-compose.yml`. В Java-коде они не захардкожены; для production лучше заменить их внешними секретами/переменными окружения.
- Исправлена ошибка исходника, где `get_services(branch_id)` обращался к несуществующей переменной `branch`.
- Логика разнесена на компоненты: Telegram, очередь, клиентский путь, CometD, Kafka, события.
- Добавлены unit-тесты для парсинга филиалов, шаблонов уведомлений и маскирования PII.

## Ограничения текущей реализации

- Состояние Telegram-пользователей хранится в памяти процесса. После рестарта пользователь должен снова выполнить `/start` и взять талон. Для production можно заменить `UserStateStore` на Redis/JDBC.
- Telegram long polling выполняется в одном потоке. Для обычного бота очереди этого достаточно; для высокой нагрузки стоит добавить очередь обработки update-ов.
- Axioma Kafka consumer включается только при наличии филиалов `queue_system=axioma`; при необходимости можно выключить через `AXIOMA_KAFKA_ENABLED=false`.

## Структура проекта

```text
src/main/java/ru/qsystems/telegrambot
  api/        служебный REST status endpoint
  cometd/     Orchestra CometD long polling client
  config/     конфигурация и парсинг филиалов
  events/     нормализация и диспетчеризация событий вызова
  kafka/      Axioma Kafka consumer
  model/      доменные модели
  path/       YAML клиентского пути
  queue/      REST gateway к Orchestra/Axioma
  telegram/   Telegram API polling, keyboard, state machine
  util/       JSON и PII utils
```
