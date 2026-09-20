# Сервіс обробки фінансових заявок

**Стек:** Java 25 · Spring Boot 4.1.1 · PostgreSQL · RabbitMQ · Flyway · Testcontainers

## Запуск

```bash
docker compose --profile app up --build   # уся система в контейнерах
./mvnw spring-boot:run                    # сервіс локально на :8081, інфраструктура в Docker
./mvnw clean verify                       # тести на Testcontainers
```

Окремо піднімати `docker compose` не треба, `spring-boot-docker-compose` підніме Postgres
і RabbitMQ з `compose.yaml` сам. RabbitMQ UI <http://localhost:15673> (`mq_client` / `secret`).

## ADR

### 1. Консистентність PostgreSQL ↔ RabbitMQ - Transactional Outbox

Статус, `order_status_history` і `outbox_message` пишуться **однією транзакцією**, причому
`OutboxAppender` оголошений з `propagation = MANDATORY`, записати подію поза бізнес-транзакцією
неможливо. `OutboxPublisher` забирає неопубліковані записи (`FOR UPDATE SKIP LOCKED`, тому кілька
інстансів не заважають один одному) і проставляє `published_at` лише після підтвердження брокера.
Гарантія **at-least-once**, якщо процес впаде між підтвердженням брокера і `published_at`,
наступний прохід опублікує подію вдруге.

*Не XA/2PC - RabbitMQ не підтримує XA. Не CDC/Debezium - тягне Kafka Connect і окремий пайплайн.*

### 2. Блокування для лімітів - атомарний `UPDATE`

`INSERT ... ON CONFLICT DO NOTHING` створює лічильник на (клієнт, добу), далі
`UPDATE ... SET reserved = reserved + :amount WHERE reserved + :amount <= daily_limit`.
`0` оновлених рядків означає, що ліміт вичерпано → `409`. Читання перед записом немає, тому немає
і гонки; вистачає `READ COMMITTED`. `CANCELLED` і `FAILED` повертають резерв рівно один раз
(прапорець `limit_released`), `COMPLETED` ні, для нього резерв стає витратою.

*Не `SELECT FOR UPDATE` - тримає рядок до кінця транзакції. Не `@Version` - при 50 запитах в один
рядок більшість спроб іде на повтор. Не `SERIALIZABLE` - ті самі ретраї плюс накладні витрати.*

### 3. Ідемпотентність - advisory-lock

`OrderService.create` починається з `pg_advisory_xact_lock` за ключем, тому конкурентні запити
серіалізуються. Другий бачить уже закомічений `idempotency_record` і отримує **ту саму заявку**
зі статусом `200`, той самий ключ з іншим тілом - `422`. Лок транзакційний, знімається на
commit або rollback сам.

### 4. Провайдер - retry і fail-fast

`@Retryable` зі Spring Framework 7, без сторонніх бібліотек. `max-retries: 2` дає **3 виклики**
з backoff 200 → 400 мс. Таймаут не ретраїться, fail-fast на вимогу ТЗ, заявка одразу йде у `FAILED`.
`@Retryable#timeout` не перериває вже заблокований виклик, тому час обмежує власний
`ProviderTimeLimiter`, виклик на віртуальному потоці плюс `future.cancel(true)` через 2 с.

### 5. Відновлення після збоїв

Виклик провайдера - поза транзакцією БД, з'єднання не утримується під час очікування.
`StuckOrderReaper` прибирає наслідки падіння процесу між транзакціями воркера: заявку в
`PROCESSING` (провайдера вже викликали, повторювати не можна) переводить у `FAILED` з поверненням
резерву, заявку в `NEW` (провайдера ще не викликали) передрайвить у чергу, а після `give-up-after`
від `created_at` закриває і її. Черга налаштована з `x-dead-letter-exchange`: інфраструктурний збій
іде в `q.orders.processing.dlq` до `ProcessingDeadLetterListener`, а не крутиться в redelivery.