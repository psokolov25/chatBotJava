# Backlog дальнейшего развития

## Статус выполнения
- ✅ Реализовано: сброс пользовательской сессии через `/reset`.
- ✅ Реализовано: базовый per-user rate limiting для message/callback.
- ✅ Реализовано: админ-API для сброса пользовательской сессии.
- ✅ Реализовано: hardening rate limiter (очистка устаревших окон, active trim, защита от роста памяти).

## P0 (приоритетно)
- Вынести `UserStateStore` из памяти процесса во внешнее хранилище (Redis/JDBC) с TTL и восстановлением диалога после рестарта.
- Добавить retry/backoff + circuit breaker для upstream Telegram/Orchestra/Axioma.
- Ввести корреляционный `requestId` в логах и в событиях REST/WS для диагностики.

## P1
- Добавить отдельный админ-endpoint для управляемого сброса сессии пользователя и безопасной переинициализации.
- ✅ Расширены тесты `TelegramUpdateHandler` на сценарии неконсистентного callback-data (`path:*` с невалидным индексом) и добавлена graceful-валидация без исключений.
- ✅ Усилена валидация REST ChatCore path-action (`path-option`/`path-other`) с явными `IllegalArgumentException` вместо неявных runtime ошибок.
- Добавить метрики Micrometer: время `createVisit`, количество ошибок по типам upstream, размер in-memory state.
  - ✅ Добавлены метрики `bot.queue_gateway.request` с тегами `operation/create_visit|get_services`, `queue_system`, `result` (success/error) для времени и ошибок upstream.

## P2
- Добавить локализацию пользовательских сообщений (ru/en) через message bundle.
- Реализовать rate limiting с различными лимитами для message и callback (раздельные policy).
- Автоматизировать валидацию `client_path.yml` в CI (schema + semantic checks).

## Что сделано в этой итерации
- Добавлена очистка устаревших окон в `UserRateLimiter` при достижении порога tracked users.
- Добавлен конфиг `rate-limit-cleanup-threshold` (`BOT_RATE_LIMIT_CLEANUP_THRESHOLD`).
- Расширены тесты `UserRateLimiterTest` (disabled mode + cleanup scenario).
- Добавлен endpoint `POST /api/bot/admin/reset-session` для сброса состояния пользователя по `userId`.

- Усилен `TelegramUpdateHandler`: для `path:<questionId>:<index>` добавлена безопасная проверка числового индекса (без падения в общий error flow).

- Добавлен `ChatCoreServiceValidationTest` на malformed `path-option` для contract-валидации REST action-flow.

- ✅ Добавлена базовая observability-метрика в status API: `userStateCount` (текущий размер in-memory `UserStateStore`).
- ✅ Добавлен корреляционный `requestId` в WebSocket `visit-event` payload и логирование dispatcher с этим id для диагностики REST/WS цепочки.

- ✅ Усилена валидация REST `path-input`: пустые значения отклоняются явной `IllegalArgumentException`; добавлен unit-тест на blank input.

- ✅ Усилена валидация REST `select-service`: отклоняются неизвестные/недоступные serviceId и confirm без выбранных услуг.

- ✅ Добавлен contract-тест на `select-service:confirm` без выбранных услуг (ожидаемая validation-ошибка).

- ✅ Добавлен contract-тест на stale multi-select: `confirm` отклоняется, если ранее выбранная услуга стала недоступной к моменту подтверждения.

- ✅ Добавлен contract-тест совместимости WS payload: legacy broadcast без requestId по-прежнему отдает поле `"requestId":""`.

- ✅ Добавлен unit-тест `VisitCallEventDispatcher`: проверка, что при `VISIT_CALL` генерируется и прокидывается непустой `requestId` в WS broadcaster.

- ✅ Добавлен unit-тест фильтра branch subscriptions в `VisitCallEventDispatcher`: событие не отправляется, если `branchPrefix` не входит в подписки пользователя.

- ✅ Добавлен contract-тест экранирования WS payload: спецсимволы (`\`, `"`, переносы строк) в `visit-event.message` корректно JSON-экранируются.

- ✅ Усилен `UserRateLimiter`: `rateLimitCleanupThreshold <= 0` безопасно нормализуется до 1 (защита от misconfiguration); добавлен unit-тест.

- ✅ Добавлен unit-тест disabled-by-window для `UserRateLimiter`: при `windowMs <= 0` лимитер всегда разрешает и не накапливает tracked users.

- ✅ Усилено contract-покрытие admin reset API: добавлен тест на `null` request и `null userId` (ожидаемая validation-ошибка).

- ✅ Продолжен hardening `UserRateLimiter`: при превышении порога и отсутствии expired окон выполняется trim самых старых tracked users (дополнительная защита от роста памяти).

- ✅ Добавлен стресс-кейс для `UserRateLimiter` с `cleanupThreshold=1`: даже при множестве уникальных users размер tracked state остается ограничен порогом.
