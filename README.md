# OTUS Microservice Architecture — User Service and Stream Processing

Репозиторий объединяет предыдущую домашнюю работу User Service и завершённую домашнюю работу Stream Processing с Billing, Order и Notification services.

Краткая схема выбранной Event Collaboration архитектуры находится в [docs/architecture.md](docs/architecture.md); актуальные HTTP и Kafka IDL — в [OpenAPI](docs/openapi.yaml) и [AsyncAPI](docs/asyncapi.yaml).


Приложение развертывается в Kubernetes, использует PostgreSQL, NGINX Ingress Controller в качестве внешней точки входа, Prometheus/Grafana для мониторинга и Elasticsearch/Fluent Bit/Kibana для централизованного логирования.

Проект выполнен в рамках домашних заданий OTUS по курсу «Microservice Architecture».

---

# Функциональность

## Authentication

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Регистрация пользователя |
| POST | `/auth/login` | Public | Аутентификация и получение JWT |

После успешной аутентификации клиент получает JWT access token.

Для обращения к защищенным ресурсам токен передается в заголовке:

```text
Authorization: Bearer <JWT>
```

## Profile API

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/profile` | Authenticated | Получить профиль текущего пользователя |
| PUT | `/profile` | Authenticated | Изменить профиль текущего пользователя |

Идентификатор текущего пользователя определяется из JWT.

Передавать `userId` при работе со своим профилем не требуется.

## User API

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/users/{id}` | Owner only | Получить пользователя |
| PUT | `/users/{id}` | Owner only | Изменить пользователя |
| DELETE | `/users/{id}` | Owner only | Удалить пользователя |

Доступ к данным другого пользователя запрещен и возвращает:

```text
HTTP 403 Forbidden
```

Создание пользователя выполняется через:

```text
POST /auth/register
```

Получение общего списка пользователей извне запрещено.

## Service endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/health/` | Public | Проверка состояния сервиса |
| GET | `/actuator/prometheus` | Public | Метрики приложения для Prometheus |

---

# Стек

## Application

- Java 21
- Spring Boot 3.5
- Spring Security
- Spring Data JDBC
- Spring Boot Actuator
- Micrometer
- JJWT 0.12.6
- PostgreSQL
- Gradle

## Infrastructure

- Docker
- Kubernetes
- Minikube
- Helm
- NGINX Ingress Controller

## Monitoring

- Prometheus
- Grafana

## Centralized Logging

- Elasticsearch 8.17.4
- Kibana 8.17.4
- Fluent Bit 3.2.10

## Testing

- JUnit
- Spring Boot Test
- Postman
- Newman

---

# Архитектура Authentication / API Gateway

Внешней точкой входа в приложение является NGINX Ingress Controller.

Отдельный API Gateway не разворачивается: функции маршрутизации внешнего HTTP-трафика в рамках текущей архитектуры выполняет NGINX Ingress.

Аутентификация и авторизация реализованы в User Service с помощью Spring Security и JWT.

```text
                       Client / Postman / Newman
                                  │
                                  │ http://arch.homework
                                  ▼
                         ┌──────────────────┐
                         │  NGINX Ingress   │
                         │   API Gateway    │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │     Service      │
                         │      :8000       │
                         └────────┬─────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
           ┌─────────────────┐         ┌─────────────────┐
           │  User Service   │         │  User Service   │
           │      Pod 1      │         │      Pod 2      │
           │                 │         │                 │
           │ Spring Security │         │ Spring Security │
           │ JWT validation  │         │ JWT validation  │
           └────────┬────────┘         └────────┬────────┘
                    │                           │
                    └─────────────┬─────────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │    PostgreSQL    │
                         │      users       │
                         └──────────────────┘
```

Для сдачи домашнего задания схема также должна быть добавлена в репозиторий как изображение, например:

```text
docs/authentication-architecture.png
```

---

# Сценарий взаимодействия

## Регистрация

```text
Client
  │
  │ POST /auth/register
  ▼
NGINX Ingress
  │
  ▼
User Service
  │
  │ BCrypt password hash
  ▼
PostgreSQL
```

Пароль пользователя не хранится в открытом виде.

Перед сохранением используется BCrypt.

---

## Login

```text
Client
  │
  │ POST /auth/login
  │ username + password
  ▼
NGINX Ingress
  │
  ▼
User Service
  │
  │ validate credentials
  │ generate JWT
  ▼
Client
  │
  │ accessToken
  ▼
```

---

## Доступ к защищенному API

```text
Client
  │
  │ Authorization: Bearer <JWT>
  ▼
NGINX Ingress
  │
  ▼
JwtAuthenticationFilter
  │
  │ validate signature
  │ validate expiration
  │ resolve user
  ▼
Spring Security Context
  │
  ▼
Controller
```

Приложение работает без HTTP session:

```text
SessionCreationPolicy.STATELESS
```

---

# JWT

JWT создается после успешного:

```text
POST /auth/login
```

Токен содержит:

```text
subject = username
userId  = user identifier
iat     = issued at
exp     = expiration
```

JWT подписывается секретным ключом.

Секрет передается приложению через Kubernetes Secret:

```text
JWT_SECRET
```

и не хранится непосредственно в `application.yml`.

Время жизни токена задается через:

```text
JWT_EXPIRATION
```

Значение по умолчанию:

```text
3600000 ms
```

то есть 1 час.

---

# Авторизация

Spring Security работает в stateless-режиме.

Публичные endpoints:

```text
/health/**
/actuator/prometheus
/auth/register
/auth/login
```

Остальные endpoints требуют аутентификацию.

Для owner-based операций используется идентификатор пользователя из authenticated principal.

Например, пользователь `user2` не может выполнить:

```text
GET /users/{user1Id}
PUT /users/{user1Id}
```

Для таких запросов возвращается:

```text
HTTP 403 Forbidden
```

---

# BFF

Отдельный BFF-сервис в текущей реализации не вводится.

В системе используется один внешний API и отсутствует необходимость в отдельных backend-интерфейсах для разных типов frontend-клиентов.

NGINX Ingress является единой внешней точкой входа, а User Service предоставляет API регистрации, аутентификации и работы с профилем.

При появлении нескольких frontend-клиентов с различающимися требованиями отдельный BFF может быть добавлен перед внутренними сервисами.

---

# Kubernetes namespace

Все основные компоненты приложения разворачиваются в namespace:

```text
m
```

Проверить namespace:

```bash
kubectl get namespace m
```

При отсутствии создать:

```bash
kubectl create namespace m
```

---

# Запуск приложения

## Требования

Для запуска необходимы:

- Docker
- kubectl
- Minikube
- Helm
- Node.js / npm — для запуска Newman

---

## 1. Запуск Kubernetes

```bash
minikube start --driver=docker
```

Проверить:

```bash
kubectl get nodes
```

---

## 2. NGINX Ingress Controller

NGINX Ingress Controller используется как внешняя точка входа/API Gateway.

Добавить Helm repository:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
```

Установить:

```bash
helm upgrade --install nginx ingress-nginx/ingress-nginx \
  --namespace m \
  --create-namespace \
  --set controller.metrics.enabled=true \
  --set controller.metrics.service.enabled=true
```

Проверить:

```bash
kubectl get pods -n m
kubectl get svc -n m
```

Отдельный API Gateway не устанавливается.

---

## 3. PostgreSQL Secret

Секреты не хранятся в репозитории. Создайте их один раз из локальных переменных окружения:

```bash
export POSTGRES_PASSWORD="$(openssl rand -hex 24)"
export JWT_SECRET="$(openssl rand -hex 32)"
./k8s/create-secrets.sh
```

Сохраните эти значения вне Git и используйте тот же JWT secret при последующих обновлениях.

---

## 4. PostgreSQL

Установить PostgreSQL:

```bash
helm upgrade --install user-service \
  oci://registry-1.docker.io/bitnamicharts/postgresql \
  -f k8s/postgres-values.yaml \
  -n m
```

Проверить:

```bash
kubectl get pods -n m
```

Дождаться состояния:

```text
Running
```

PostgreSQL доступен внутри Kubernetes как:

```text
user-service-postgresql
```

---

## 5. ConfigMap

Применить конфигурацию приложения и SQL migration:

```bash
kubectl apply -f k8s/02-configmap.yaml -n m
kubectl apply -f k8s/03-migration-configmap.yaml -n m
```

---

## 6. JWT Secret

JWT signing key хранится отдельно в Kubernetes Secret.

JWT Secret создаёт `k8s/create-secrets.sh` вместе с секретом PostgreSQL. Не меняйте его при обычном обновлении приложения, иначе ранее выданные токены перестанут проходить проверку.

Проверить наличие Secret:

```bash
kubectl get secret user-service-jwt-secret -n m
```

---

## 7. Database migration

При повторном развертывании удалить предыдущий Job:

```bash
kubectl delete job user-service-migration \
  -n m \
  --ignore-not-found
```

Запустить migration:

```bash
kubectl apply -f k8s/04-migration-job.yaml -n m
```

Дождаться завершения:

```bash
kubectl wait \
  --for=condition=complete \
  job/user-service-migration \
  -n m \
  --timeout=120s
```

Проверить:

```bash
kubectl get jobs -n m
kubectl logs job/user-service-migration -n m
```

Job должен иметь статус:

```text
Complete
```

---

## 8. User Service

Применить Deployment:

```bash
kubectl apply -f k8s/05-deployment.yaml -n m
```

Применить Service:

```bash
kubectl apply -f k8s/06-service.yaml -n m
```

Применить Ingress:

```bash
kubectl apply -f k8s/07-ingress.yaml -n m
```

Проверить:

```bash
kubectl get pods -n m
kubectl get svc -n m
kubectl get ingress -n m
```

Deployment запускает две реплики User Service. Stream Processing deployment и порядок запуска четырёх сервисов приведены в разделе [Развёртывание Stream Processing](#развёртывание-stream-processing).

---

# Доступ через Ingress

Добавить в:

```text
/etc/hosts
```

строку:

```text
127.0.0.1 arch.homework
```

Запустить в отдельном терминале:

```bash
minikube tunnel
```

Проверить:

```bash
curl -i http://arch.homework/health/
```

Ожидаемый ответ:

```http
HTTP/1.1 200 OK
```

```json
{
  "status": "OK"
}
```

---

# Authentication API

## Регистрация пользователя

```bash
curl -i -X POST http://arch.homework/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "otus-user",
    "password": "Password123!",
    "firstName": "Otus",
    "lastName": "Student",
    "email": "otus-user@example.com",
    "phone": "+79990000001"
  }'
```

Ожидаемый статус:

```text
201 Created
```

Пароль в response не возвращается.

---

## Login

```bash
curl -i -X POST http://arch.homework/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "otus-user",
    "password": "Password123!"
  }'
```

Пример ответа:

```json
{
  "accessToken": "<JWT>"
}
```

---

## Получение профиля

```bash
curl -i http://arch.homework/profile \
  -H "Authorization: Bearer <JWT>"
```

Без JWT:

```text
401 Unauthorized
```

С корректным JWT:

```text
200 OK
```

---

## Изменение профиля

```bash
curl -i -X PUT http://arch.homework/profile \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Otus Updated",
    "lastName": "Student Updated",
    "email": "otus-user-updated@example.com",
    "phone": "+79990000002"
  }'
```

Ожидаемый статус:

```text
200 OK
```

---

# Postman / Newman

Postman collection:

```text
postman/authentication-bff.postman_collection.json
```

Postman environment:

```text
postman/otus-local.postman_environment.json
```

Environment содержит:

```text
baseUrl = http://arch.homework
```

Для каждого запуска коллекция генерирует уникальный `runId`.

На его основе создаются уникальные:

```text
username
email
```

поэтому тесты можно запускать повторно без конфликтов уникальности в PostgreSQL.

---

## Тестовый сценарий

Коллекция последовательно выполняет:

```text
1. Register user1
        ↓
2. GET /profile without authentication → 401
        ↓
3. PUT /profile without authentication → 401
        ↓
4. Login user1
        ↓
5. Update user1 profile
        ↓
6. Get user1 profile and verify changes
        ↓
7. Register user2
        ↓
8. Login user2
        ↓
9. user2 → GET /users/{user1Id} → 403
        ↓
10. user2 → PUT /users/{user1Id} → 403
```

Logout endpoint отсутствует, поскольку используется stateless JWT authentication и серверная HTTP session не создается.

---

## Запуск Newman

```bash
npx newman run postman/authentication-bff.postman_collection.json \
  -e postman/otus-local.postman_environment.json
```

Коллекция выводит request и response непосредственно в CLI.

Успешный результат:

```text
iterations             1        failed 0
requests              10        failed 0
test-scripts          20        failed 0
prerequest-scripts    10        failed 0
assertions            11        failed 0
```

---

# Проверка ограничения доступа

## Без аутентификации

```bash
curl -i http://arch.homework/profile
```

Результат:

```text
HTTP 401 Unauthorized
```

---

## Доступ к чужому пользователю

При попытке аутентифицированного `user2` получить профиль `user1`:

```bash
curl -i http://arch.homework/users/<USER1_ID> \
  -H "Authorization: Bearer <USER2_JWT>"
```

результат:

```text
HTTP 403 Forbidden
```

Аналогичное ограничение действует для изменения:

```text
PUT /users/<USER1_ID>
```

---

# Docker

Корневой [Dockerfile](Dockerfile) собирает отдельный образ по параметру `SERVICE`. Команды сборки для Minikube приведены ниже; для registry добавьте свой registry/tag и команду `docker push`.

---

# Kubernetes manifests

```text
k8s/
├── create-secrets.sh
├── 02-configmap.yaml
├── 03-migration-configmap.yaml
├── 04-migration-job.yaml
├── 05-deployment.yaml
├── 06-service.yaml
├── 07-ingress.yaml
├── postgres-values.yaml
└── logging/
    ├── 01-namespace.yaml
    ├── 02-elasticsearch-headless-service.yaml
    ├── 03-elasticsearch-service.yaml
    ├── 04-elasticsearch-statefulset.yaml
    ├── 05-kibana-deployment.yaml
    ├── 06-kibana-service.yaml
    ├── 07-fluent-bit-service-account.yaml
    ├── 08-fluent-bit-cluster-role.yaml
    ├── 09-fluent-bit-cluster-role-binding.yaml
    ├── 10-fluent-bit-configmap.yaml
    └── 11-fluent-bit-daemonset.yaml
```

Основные ресурсы:

- `create-secrets.sh` — создаёт Kubernetes Secrets из переменных окружения, значения не коммитятся;
- `02-configmap.yaml` — конфигурация User Service;
- `03-migration-configmap.yaml` — SQL migration;
- `04-migration-job.yaml` — Kubernetes Job для миграции;
- `05-deployment.yaml` — Deployment User Service;
- `06-service.yaml` — ClusterIP Service;
- `07-ingress.yaml` — Ingress для `arch.homework`;
- `11-kafka.yaml` — Kafka KRaft broker и topics;
- `12-stream-services.yaml` — ConfigMaps, Deployments и Services Billing/Order/Notification;
- `13-databases-job.yaml` — создание отдельных баз сервисов;
- `14-stream-ingress.yaml` — маршрутизация публичных API четырёх сервисов;
- `postgres-values.yaml` — PostgreSQL Helm values.

JWT signing key создается отдельным Kubernetes Secret:

```text
user-service-jwt-secret
```

---

# Stream Processing

## Архитектура

Система состоит из четырёх сервисов с отдельными базами данных. Внешние команды выполняются по HTTP; взаимодействие бизнес-сервисов выполняется через Kafka по схеме Event Collaboration. Регистрация создаёт `UserRegistered`, создание заказа создаёт `OrderCreated`, а Billing публикует `PaymentSucceeded` или `PaymentFailed`. User/Order/Billing используют transactional outbox. Consumer groups Order и Notification независимы.

Полные схемы вариантов `0. Только HTTP`, `1. HTTP + события уведомлений`, `2. Event Collaboration` и выбранного варианта `3` с Mermaid-исходниками и изображениями SVG находятся в [docs/architecture.md](docs/architecture.md). Контракты: [OpenAPI](docs/openapi.yaml), [AsyncAPI](docs/asyncapi.yaml).

![Выбранная Event Collaboration архитектура](docs/diagrams/selected-architecture.svg)

## Сервисы и API

| Сервис | Ответственность | Публичные маршруты | База |
|---|---|---|---|
| User Service | Регистрация, login, JWT, профиль | `/auth/register`, `/auth/login`, `/profile`, `/users/{id}` | `users` |
| Billing Service | Счета, баланс, пополнение, списание и история | `/billing/accounts/me`, `/billing/accounts/me/deposit` | `billing` |
| Order Service | Заказы и статус оплаты | `POST /orders`, `GET /orders/{id}` | `orders` |
| Notification Service | Сохранение результатов оплаты | `GET /notifications` | `notifications` |

Billing, Order и Notification валидируют подпись, срок действия и `userId` JWT с тем же `JWT_SECRET`, что и User Service. Все публичные операции определяют владельца из проверенного токена. Email для сообщений попадает в новый JWT claim при login и затем переносится через события.

## Kafka topics

| Topic | Producer | Consumers |
|---|---|---|
| `users.registered` | User Service | Billing Service (`billing-service`) |
| `orders.created` | Order Service | Billing Service (`billing-service`) |
| `payments.succeeded` | Billing Service | Order Service (`order-service`), Notification Service (`notification-service`) |
| `payments.failed` | Billing Service | Order Service (`order-service`), Notification Service (`notification-service`) |

События содержат UUID `eventId`, UTC `occurredAt`, Long идентификаторы, email и сумму. Суммы внутри приложений хранятся как `BigDecimal` / `NUMERIC(19,2)`. Billing использует уникальный order ID, Order дедуплицирует event ID и обновляет только `PENDING`, Notification имеет уникальный `event_id` и сохраняет email получателя и сумму.

## Локальная сборка и тесты

Из корня проекта:

```bash
./gradlew clean test
./gradlew :user-service:bootJar :billing-service:bootJar :order-service:bootJar :notification-service:bootJar
```

Testcontainers-тесты Billing проверяют создание счёта, пополнение, ошибки суммы, достаточный/недостаточный баланс, повторный order ID, два конкурентных списания и отсутствующий счёт на PostgreSQL. Notification интеграционно проверяет уникальность и фильтр пользователя. Остальные User, Order, Notification service и JWT security тесты запускаются в том же `test`.

## Docker build

Соберите четыре отдельных образа. Для локального Minikube можно собирать образы непосредственно в Docker daemon Minikube:

```bash
minikube start --driver=docker
eval "$(minikube docker-env)"
docker build -t user-service:stream-processing --build-arg SERVICE=user-service --build-arg HTTP_PORT=8000 .
docker build -t billing-service:stream-processing --build-arg SERVICE=billing-service --build-arg HTTP_PORT=8001 .
docker build -t order-service:stream-processing --build-arg SERVICE=order-service --build-arg HTTP_PORT=8002 .
docker build -t notification-service:stream-processing --build-arg SERVICE=notification-service --build-arg HTTP_PORT=8003 .
```

Корневой Dockerfile использует Gradle multi-module build и включает общий модуль контрактов и JWT/outbox. Для внешнего registry поменяйте теги на `REGISTRY/...` и отправьте каждый образ через `docker push`.

**Альтернативный способ**, если образы собраны в обычном локальном Docker daemon (без `eval "$(minikube docker-env)"`):

```bash
docker build -t user-service:stream-processing --build-arg SERVICE=user-service --build-arg HTTP_PORT=8000 .
minikube image load user-service:stream-processing
minikube image ls | grep user-service
```

Аналогично загрузите образы Billing, Order и Notification, если собирали их вне Minikube. Убедитесь, что Deployment использует тег `*:stream-processing` и `imagePullPolicy: IfNotPresent`. Не смешивайте способы сборки без проверки текущего Docker context/daemon.

## Развёртывание Kubernetes

Манифесты используют namespace `m`. Существующий PostgreSQL Helm release и logging/monitoring ресурсы сохраняются. Все команды `kubectl` ниже указывают namespace явно. Система развертывается в Minikube без удаления существующих ресурсов.

1. Создайте namespace и секреты. Секреты сохраняйте вне Git и между обновлениями используйте прежние значения:

```bash
minikube start --driver=docker
kubectl get namespace m >/dev/null 2>&1 || kubectl create namespace m
export POSTGRES_PASSWORD="$(openssl rand -hex 24)"
export JWT_SECRET="$(openssl rand -hex 32)"
./k8s/create-secrets.sh
```

2. Установите NGINX Ingress и PostgreSQL:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install nginx ingress-nginx/ingress-nginx --namespace m --create-namespace
helm upgrade --install user-service oci://registry-1.docker.io/bitnamicharts/postgresql \
  -f k8s/postgres-values.yaml --namespace m
kubectl rollout status statefulset/user-service-postgresql --namespace m --timeout=300s
```

3. Разверните Kafka и создайте topics:

```bash
kubectl apply -f k8s/11-kafka.yaml --namespace m
kubectl rollout status statefulset/kafka --namespace m --timeout=300s
kubectl wait --for=condition=complete job/kafka-topics --namespace m --timeout=180s
```

4. Примените ConfigMaps и создайте три отдельные базы Billing, Order, Notification. User база `users` создаётся PostgreSQL Helm values:

```bash
kubectl apply -f k8s/02-configmap.yaml --namespace m
kubectl apply -f k8s/03-migration-configmap.yaml --namespace m
kubectl apply -f k8s/12-stream-services.yaml --namespace m
kubectl apply -f k8s/13-databases-job.yaml --namespace m
kubectl wait --for=condition=complete job/stream-processing-databases --namespace m --timeout=180s
```

5. Создайте/обновите пользовательскую миграцию и приложения:

```bash
kubectl delete job user-service-migration --namespace m --ignore-not-found
kubectl apply -f k8s/04-migration-job.yaml --namespace m
kubectl wait --for=condition=complete job/user-service-migration --namespace m --timeout=180s
kubectl apply -f k8s/05-deployment.yaml --namespace m
kubectl apply -f k8s/06-service.yaml --namespace m
kubectl apply -f k8s/07-ingress.yaml --namespace m
kubectl apply -f k8s/14-stream-ingress.yaml --namespace m
kubectl rollout status deployment/user-service --namespace m --timeout=300s
kubectl rollout status deployment/billing-service --namespace m --timeout=300s
kubectl rollout status deployment/order-service --namespace m --timeout=300s
kubectl rollout status deployment/notification-service --namespace m --timeout=300s
```

6. Добавьте в `/etc/hosts` запись `127.0.0.1 arch.homework`, запустите `minikube tunnel` и проверьте:

```bash
curl -i http://arch.homework/health/
kubectl get pods,services,ingress -n m
```

Пробы сервисов используют `/health/` или Actuator readiness/liveness. Kafka в учебной конфигурации — один KRaft broker с временным `emptyDir`; PostgreSQL остаётся на существующем persistent Helm volume.

## Postman / Newman: сквозной сценарий

Коллекция [stream-processing.postman_collection.json](postman/stream-processing.postman_collection.json) и environment [stream-processing.postman_environment.json](postman/stream-processing.postman_environment.json) по умолчанию используют `http://arch.homework`. Запуск из корня:

```bash
./postman/run-stream-processing.sh
```

Коллекция генерирует уникальные username/email, сохраняет JWT и order IDs, ждёт асинхронные результаты polling-запросами с пределом 30 попыток. Runner задерживает каждый запрос на 1 секунду и печатает request/response headers, тела и статусы.

Сценарий проверяет создание Billing-счёта после регистрации, пополнение на 1500, списание 700, остаток 800, положительное уведомление, затем заказ на 2000, неизменный остаток 800 и отрицательное уведомление с причиной `INSUFFICIENT_BALANCE`.

Последний проверенный E2E-прогон (12 октября 2026 года) через NGINX Ingress завершился успешно: **1 итерация, 45 HTTP-запросов, 40 assertions, 0 ошибок, 4,8 секунды**. Число запросов зависит от количества polling-повторов.

Прямой запуск коллекции:

```bash
npx newman run postman/stream-processing.postman_collection.json \
  -e postman/stream-processing.postman_environment.json \
  --timeout-request 15000
```

В проверенном прогоне были подтверждены регистрация, создание счёта через событие, пополнение, успешное списание ровно один раз, уведомление об успехе, отказ при недостатке средств, неизменность баланса и уведомление об отказе. Это подтверждает E2E-сценарии, но не является отдельным тестом восстановления после сбоев или всех сценариев дублирования событий.

## Диагностика Outbox и Kafka

User Service использует transactional outbox: после регистрации запись о событии `users.registered` попадает в таблицу `users.outbox_events`, а фоновый publisher отправляет её в Kafka. **Перед E2E-тестом необходимо выполнить `k8s/04-migration-job.yaml`**, включающий `V2__create_outbox.sql`. Убедитесь, что `user-service-config` содержит `KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"`.

```bash
kubectl get configmap user-service-config -n m -o yaml
kubectl get jobs -n m
kubectl logs -n m job/user-service-migration
kubectl logs -n m deployment/user-service --since=5m --tail=100
```

Если в логах появляется `ERROR: relation "outbox_events" does not exist`, проверьте, что SQL ConfigMap обновлён и миграционный Job был **запущен заново после обновления SQL**. Уже завершённый Kubernetes Job не выполняется повторно от `kubectl apply`:

```bash
kubectl apply -n m -f k8s/03-migration-configmap.yaml
kubectl delete job user-service-migration -n m --ignore-not-found
kubectl apply -n m -f k8s/04-migration-job.yaml
kubectl wait -n m --for=condition=complete job/user-service-migration --timeout=180s
kubectl logs -n m job/user-service-migration
```

Эти команды удаляют только объект Job, а не PostgreSQL и не данные пользователей. При ошибках миграции проверьте логи перед повторной попыткой. После успешной миграции можно повторить Newman.

## Известные ограничения

- Kafka развёрнута одним брокером без TLS/SASL и без постоянного volume; это конфигурация локальной учебной среды.
- Consumer повторяет обработку до четырёх раз с интервалом в секунду после первой попытки; после исчерпания попыток сообщение публикуется в соответствующий `.DLT` topic. Для заказа без billing-счёта Billing после этих попыток публикует `PaymentFailed` с причиной `ACCOUNT_NOT_FOUND` через transactional outbox.
- Outbox rows хранятся после публикации; для production нужно добавить политику очистки и наблюдение за lag/числом retry.
- PostgreSQL базы логически разделены в одном PostgreSQL deployment; для этого задания они не вынесены в отдельные кластеры.
- Notification Service сохраняет email получателя, сумму, тему и текст email-уведомления, а API возвращает эти данные. Фактическая SMTP-отправка в рамках проекта не настроена.
- Event Collaboration обеспечивает eventual consistency: после POST регистрации/заказа счет и итоговый статус могут появиться позже.

---

# Monitoring

Prometheus собирает метрики User Service и NGINX Ingress Controller.

User Service endpoint:

```text
/actuator/prometheus
```

Основная HTTP-метрика:

```text
http_server_requests_seconds
```

Она используется для анализа:

- RPS;
- latency;
- HTTP 5xx.

NGINX Ingress предоставляет:

```text
nginx_ingress_controller_requests
nginx_ingress_controller_request_duration_seconds
```

---

# Prometheus и Grafana

Проверить компоненты:

```bash
kubectl get pods -n monitoring
kubectl get svc -n monitoring
```

Для доступа к Grafana:

```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
```

Grafana:

```text
http://localhost:3000
```

Dashboard:

```text
User-service
```

Содержит панели:

```text
User Service — RPS by API
User Service — Latency by API
User Service — 500 Error Rate by API

NGINX Ingress — RPS
NGINX Ingress — Latency
NGINX Ingress — 500 Error Rate
```

---

# Alerting

В Grafana настроен alert:

```text
User Service — High Error Rate
```

Он отслеживает появление HTTP `5xx` ответов User Service.

При сохранении ошибок в течение pending period alert переходит в:

```text
Firing
```

---

# Централизованное логирование

Для централизованного логирования используется EFK:

```text
User Service
     │
     │ stdout
     ▼
/var/log/containers/*.log
     │
     ▼
 Fluent Bit
     │
     ▼
Elasticsearch
     │
     ▼
   Kibana
```

User Service пишет structured JSON logs в `stdout`.

Используется конфигурация Spring Boot:

```yaml
logging:
  structured:
    format:
      console: logstash
```

Для каждого HTTP request создается:

```text
request_id
```

Он помещается в MDC и используется для связывания логов одного запроса.

---

# Elasticsearch

Elasticsearch работает в namespace:

```text
logging
```

Проверить:

```bash
kubectl get pods -n logging
kubectl get svc -n logging
kubectl get pvc -n logging
```

Локальный доступ:

```bash
kubectl port-forward -n logging svc/elasticsearch 9200:9200
```

Проверка:

```bash
curl http://localhost:9200
```

Индексы:

```bash
curl 'http://localhost:9200/_cat/indices/fluent-bit-logs-*?v'
```

---

# Fluent Bit

Fluent Bit работает как Kubernetes DaemonSet и читает:

```text
/var/log/containers/*.log
```

К логам добавляются Kubernetes metadata:

- pod name;
- namespace;
- container name;
- labels;
- container image;
- pod IP;
- host.

Проверить:

```bash
kubectl get daemonset -n logging
kubectl get pods -n logging -l app=fluent-bit
```

---

# Kibana

Локальный доступ:

```bash
kubectl port-forward -n logging svc/kibana 5601:5601
```

Kibana:

```text
http://localhost:5601
```

Data View:

```text
Fluent Bit Logs
```

Index pattern:

```text
fluent-bit-logs-*
```

Для поиска логов User Service:

```text
kubernetes.container_name: "user-service"
```

Для поиска WARN:

```text
kubernetes.container_name: "user-service" and level: "WARN"
```

Для поиска операций пользователя:

```text
kubernetes.container_name: "user-service" and user_id: <USER_ID>
```

---

# Проверка состояния Kubernetes

```bash
kubectl get pods -n m
kubectl get svc -n m
kubectl get ingress -n m
kubectl get jobs -n m
kubectl get deployments,statefulsets -n m
```

Ожидается:

```text
NGINX Ingress Controller    Running
User Service Pod 1          Running
User Service Pod 2          Running
Billing Service             Running
Order Service               Running
Notification Service        Running
Kafka broker                Running
PostgreSQL                  Running
Migration Job               Complete
Kafka topics Job            Complete
Stream databases Job        Complete
```

---

# Удаление ресурсов

## User Service

```bash
kubectl delete -f k8s/07-ingress.yaml -n m
kubectl delete -f k8s/06-service.yaml -n m
kubectl delete -f k8s/05-deployment.yaml -n m
kubectl delete -f k8s/04-migration-job.yaml -n m
kubectl delete -f k8s/03-migration-configmap.yaml -n m
kubectl delete -f k8s/02-configmap.yaml -n m
```

## PostgreSQL

```bash
helm uninstall user-service -n m
```

```bash
kubectl delete secret user-service-db-secret -n m
```

## JWT Secret

```bash
kubectl delete secret user-service-jwt-secret -n m
```

## Centralized logging

```bash
kubectl delete -f k8s/logging/11-fluent-bit-daemonset.yaml
kubectl delete -f k8s/logging/10-fluent-bit-configmap.yaml
kubectl delete -f k8s/logging/09-fluent-bit-cluster-role-binding.yaml
kubectl delete -f k8s/logging/08-fluent-bit-cluster-role.yaml
kubectl delete -f k8s/logging/07-fluent-bit-service-account.yaml
kubectl delete -f k8s/logging/06-kibana-service.yaml
kubectl delete -f k8s/logging/05-kibana-deployment.yaml
kubectl delete -f k8s/logging/04-elasticsearch-statefulset.yaml
kubectl delete -f k8s/logging/03-elasticsearch-service.yaml
kubectl delete -f k8s/logging/02-elasticsearch-headless-service.yaml
kubectl delete -f k8s/logging/01-namespace.yaml
```

## Monitoring

```bash
helm uninstall prometheus -n monitoring
```

## Minikube

```bash
minikube stop
```
