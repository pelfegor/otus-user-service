# User Service

REST API для управления пользователями с аутентификацией и авторизацией на основе JWT.

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

Применить Secret с параметрами PostgreSQL:

```bash
kubectl apply -f k8s/01-secret.yaml -n m
```

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

Создать его необходимо один раз:

```bash
kubectl get secret user-service-jwt-secret -n m >/dev/null 2>&1 || \
kubectl create secret generic user-service-jwt-secret \
  --from-literal=JWT_SECRET="$(openssl rand -hex 32)" \
  -n m
```

При обычном обновлении приложения Secret не следует генерировать заново, так как после смены signing key ранее выданные JWT перестанут проходить проверку.

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

Deployment запускает две реплики User Service.

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

Docker image:

```text
pelfegor/user-service:1.3.0
```

Образ собирается для:

```text
linux/amd64
linux/arm64
```

Сборка и публикация:

```bash
./gradlew clean test bootJar

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t pelfegor/user-service:1.3.0 \
  --push .
```

---

# Kubernetes manifests

```text
k8s/
├── 01-secret.yaml
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

- `01-secret.yaml` — credentials PostgreSQL;
- `02-configmap.yaml` — конфигурация User Service;
- `03-migration-configmap.yaml` — SQL migration;
- `04-migration-job.yaml` — Kubernetes Job для миграции;
- `05-deployment.yaml` — Deployment User Service;
- `06-service.yaml` — ClusterIP Service;
- `07-ingress.yaml` — Ingress для `arch.homework`;
- `postgres-values.yaml` — PostgreSQL Helm values.

JWT signing key создается отдельным Kubernetes Secret:

```text
user-service-jwt-secret
```

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
```

Ожидается:

```text
NGINX Ingress Controller    Running
User Service Pod 1          Running
User Service Pod 2          Running
PostgreSQL                  Running
Migration Job               Complete
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
kubectl delete -f k8s/01-secret.yaml -n m
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