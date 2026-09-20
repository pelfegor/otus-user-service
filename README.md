# User Service

RESTful CRUD-сервис для управления пользователями, развернутый в Kubernetes с PostgreSQL, мониторингом через Prometheus и визуализацией метрик в Grafana.

Проект выполнен в рамках домашних заданий OTUS по микросервисной архитектуре.

## Функциональность

Сервис предоставляет CRUD API для управления пользователями:

| Method | Endpoint | Description |
|---|---|---|
| POST | `/users` | Создать пользователя |
| GET | `/users/{id}` | Получить пользователя |
| GET | `/users` | Получить список пользователей |
| PUT | `/users/{id}` | Обновить пользователя |
| DELETE | `/users/{id}` | Удалить пользователя |
| GET | `/health/` | Проверка состояния сервиса |
| GET | `/actuator/prometheus` | Метрики приложения для Prometheus |

## Стек

- Java 21
- Spring Boot 3.5
- Spring Data JDBC
- Spring Boot Actuator
- Micrometer
- PostgreSQL
- Gradle
- Docker
- Kubernetes
- Helm
- NGINX Ingress Controller
- Prometheus
- Grafana
- Postman / Newman

## Архитектура

```text
                              ┌──────────────────┐
                              │  arch.homework   │
                              └────────┬─────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │  NGINX Ingress   │
                              └────────┬─────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │     Service      │
                              │      :8000       │
                              └────────┬─────────┘
                                       │
                          ┌────────────┴────────────┐
                          ▼                         ▼
                   ┌─────────────┐           ┌─────────────┐
                   │ User Service│           │ User Service│
                   │    Pod 1    │           │    Pod 2    │
                   └──────┬──────┘           └──────┬──────┘
                          │                         │
                          └────────────┬────────────┘
                                       ▼
                              ┌──────────────────┐
                              │    PostgreSQL    │
                              └──────────────────┘


                   ┌───────────────────────────────┐
                   │          Monitoring           │
                   │                               │
User Service ─────►│ Prometheus ─────► Grafana    │
NGINX Ingress ────►│                               │
                   └───────────────────────────────┘
```

Конфигурация приложения хранится в Kubernetes `ConfigMap`.

Данные для подключения к PostgreSQL хранятся в Kubernetes `Secret`.

Первоначальная миграция базы данных выполняется отдельным Kubernetes `Job`.

Метрики User Service и NGINX Ingress собираются Prometheus и отображаются в Grafana.

---

# Запуск приложения

## Требования

Для запуска необходимы:

- Docker
- kubectl
- Minikube
- Helm
- Node.js / npm — для запуска Newman

## 1. Запуск Kubernetes

Запустить Minikube:

```bash
minikube start --driver=docker
```

Проверить состояние:

```bash
kubectl get nodes
```

## 2. NGINX Ingress Controller

Добавить Helm-репозиторий:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
```

Установить NGINX Ingress Controller:

```bash
helm install nginx ingress-nginx/ingress-nginx \
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

Для сбора метрик NGINX Ingress Controller должен предоставлять metrics endpoint на порту `10254`.

## 3. Установка PostgreSQL

Добавить Helm-репозиторий Bitnami:

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

Создать Secret с данными подключения:

```bash
kubectl apply -f k8s/01-secret.yaml
```

Установить PostgreSQL:

```bash
helm install user-service-postgres \
  bitnami/postgresql \
  -f k8s/postgres-values.yaml
```

Дождаться запуска PostgreSQL:

```bash
kubectl wait \
  --for=condition=Ready \
  pod \
  -l app.kubernetes.io/instance=user-service-postgres \
  --timeout=180s
```

Проверить:

```bash
kubectl get pods
```

## 4. Первоначальная миграция

Создать ConfigMap приложения и ConfigMap с SQL-миграцией:

```bash
kubectl apply -f k8s/02-configmap.yaml
kubectl apply -f k8s/03-migration-configmap.yaml
```

Запустить migration Job:

```bash
kubectl apply -f k8s/04-migration-job.yaml
```

Дождаться успешного завершения:

```bash
kubectl wait \
  --for=condition=complete \
  job/user-service-migration \
  --timeout=120s
```

Проверить:

```bash
kubectl get jobs
kubectl logs job/user-service-migration
```

Job должен иметь статус `Complete`.

## 5. Запуск User Service

Применить Deployment, Service и Ingress:

```bash
kubectl apply -f k8s/05-deployment.yaml
kubectl apply -f k8s/06-service.yaml
kubectl apply -f k8s/07-ingress.yaml
```

Проверить:

```bash
kubectl get pods
kubectl get svc
kubectl get ingress
```

Deployment запускает две реплики приложения.

Оба pod должны находиться в состоянии:

```text
READY   STATUS
1/1     Running
```

## 6. Доступ через Ingress

Добавить в `/etc/hosts`:

```text
127.0.0.1 arch.homework
```

Запустить Minikube tunnel в отдельном терминале:

```bash
minikube tunnel
```

Проверить приложение:

```bash
curl http://arch.homework/health/
```

Ожидаемый ответ:

```json
{
  "status": "OK"
}
```

Проверить API:

```bash
curl http://arch.homework/users
```

---

# CRUD API

## Создание пользователя

```bash
curl -X POST http://arch.homework/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "otus-user",
    "firstName": "Otus",
    "lastName": "Student",
    "email": "otus-user@example.com",
    "phone": "+79990000001"
  }'
```

Успешный ответ:

```text
HTTP 201 Created
```

## Получение пользователя

```bash
curl http://arch.homework/users/1
```

## Обновление пользователя

```bash
curl -X PUT http://arch.homework/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "username": "otus-user",
    "firstName": "Otus Updated",
    "lastName": "Student Updated",
    "email": "otus-user-updated@example.com",
    "phone": "+79990000002"
  }'
```

## Удаление пользователя

```bash
curl -X DELETE http://arch.homework/users/1
```

Успешный ответ:

```text
HTTP 204 No Content
```

---

# Мониторинг

## Метрики User Service

Приложение использует Spring Boot Actuator и Micrometer для экспорта метрик в формате Prometheus.

Endpoint:

```text
/actuator/prometheus
```

Prometheus собирает метрики непосредственно с pod'ов User Service.

Основная HTTP-метрика:

```text
http_server_requests_seconds
```

Она используется для построения:

- RPS по API;
- latency;
- количества HTTP 5xx ошибок.

Системный endpoint `/actuator/prometheus` исключается из прикладных графиков, чтобы запросы самого Prometheus не влияли на статистику API.

## Метрики NGINX Ingress

NGINX Ingress Controller экспортирует собственные Prometheus-метрики.

Используемые метрики:

```text
nginx_ingress_controller_requests
nginx_ingress_controller_request_duration_seconds
```

На их основе отображаются:

- RPS через Ingress;
- latency Ingress;
- HTTP 5xx error rate.

---

# Prometheus и Grafana

Prometheus и Grafana используются для мониторинга приложения и Ingress Controller.

Проверить запущенные компоненты мониторинга:

```bash
kubectl get pods -n monitoring
kubectl get svc -n monitoring
```

Для локального доступа к Grafana можно использовать port-forward:

```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
```

После этого Grafana доступна по адресу:

```text
http://localhost:3000
```

Prometheus используется в Grafana в качестве datasource.

---

# Grafana Dashboard

Создан dashboard:

```text
User-service
```

Dashboard содержит следующие панели.

### User Service

- `User Service — RPS by API`
- `User Service — Latency by API`
- `User Service — 500 Error Rate by API`

Метрики группируются по API и HTTP method.

### NGINX Ingress

- `NGINX Ingress — RPS`
- `NGINX Ingress — Latency`
- `NGINX Ingress — 500 Error Rate`

Таким образом можно отдельно наблюдать поведение самого приложения и входящего трафика через Ingress.

Примеры PromQL-запросов.

RPS приложения:

```promql
sum by (method, uri) (
  rate(
    http_server_requests_seconds_count{
      job="user-service",
      uri!="/actuator/prometheus"
    }[1m]
  )
)
```

500 errors:

```promql
sum by (method, uri) (
  rate(
    http_server_requests_seconds_count{
      job="user-service",
      status=~"5..",
      uri!="/actuator/prometheus"
    }[1m]
  )
)
```

NGINX Ingress RPS:

```promql
sum(
  rate(
    nginx_ingress_controller_requests{
      ingress="user-service-ingress"
    }[1m]
  )
)
```

---

# Alerting

В Grafana настроен alert:

```text
User Service — High Error Rate
```

Alert отслеживает появление HTTP `5xx` ответов User Service.

PromQL:

```promql
sum(
  rate(
    http_server_requests_seconds_count{
      job="user-service",
      status=~"5..",
      uri!="/actuator/prometheus"
    }[1m]
  )
) or vector(0)
```

Условие:

```text
value > 0
```

Pending period:

```text
1m
```

Если сервис продолжает возвращать `5xx` ошибки в течение заданного периода, alert переходит в состояние:

```text
Firing
```

Для учебного окружения используется тестовый contact point `empty`, поэтому реальная отправка уведомлений во внешнюю систему не выполняется.

---

# Postman / Newman

Postman collection находится в:

```text
postman/user-service.postman_collection.json
```

Коллекция последовательно выполняет:

```text
Create user
    ↓
Get user
    ↓
Update user
    ↓
Delete user
```

ID созданного пользователя автоматически сохраняется в collection variable и используется следующими запросами.

Для каждого запуска генерируется уникальный `runId`, поэтому коллекцию можно запускать повторно без конфликта уникальных `username` и `email`.

Запуск:

```bash
npx newman run postman/user-service.postman_collection.json
```

При успешном выполнении:

```text
iterations        1   failed 0
requests          4   failed 0
test-scripts      4   failed 0
assertions        7   failed 0
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
└── postgres-values.yaml
```

Назначение ресурсов:

- `01-secret.yaml` — credentials PostgreSQL
- `02-configmap.yaml` — конфигурация User Service
- `03-migration-configmap.yaml` — SQL первоначальной миграции
- `04-migration-job.yaml` — Kubernetes Job для выполнения миграции
- `05-deployment.yaml` — Deployment приложения с двумя репликами
- `06-service.yaml` — ClusterIP Service
- `07-ingress.yaml` — Ingress для `arch.homework`
- `postgres-values.yaml` — values для PostgreSQL Helm chart

---

# Docker

Docker image:

```text
pelfegor/user-service:1.0.0
```

Образ поддерживает:

```text
linux/amd64
linux/arm64
```

Сборка и публикация multi-platform image:

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t pelfegor/user-service:1.0.0 \
  --push .
```

---

# Проверка мониторинга

Для генерации нагрузки можно выполнить несколько запросов к API:

```bash
for i in {1..20}; do
  curl -s http://arch.homework/health/ > /dev/null
done
```

После этого запросы отображаются в метриках NGINX Ingress.

Для проверки alert необходимо сгенерировать HTTP `5xx` ответы приложения и поддерживать их появление дольше `Pending period`.

После выполнения условия alert:

```text
User Service — High Error Rate
```

переходит в состояние:

```text
Firing
```

Результат можно проверить в:

```text
Grafana → Alerting → Alert rules
```

---

# Удаление ресурсов

Удалить приложение:

```bash
kubectl delete -f k8s/07-ingress.yaml
kubectl delete -f k8s/06-service.yaml
kubectl delete -f k8s/05-deployment.yaml
kubectl delete -f k8s/04-migration-job.yaml
kubectl delete -f k8s/03-migration-configmap.yaml
kubectl delete -f k8s/02-configmap.yaml
```

Удалить PostgreSQL:

```bash
helm uninstall user-service-postgres
```

Удалить Secret:

```bash
kubectl delete -f k8s/01-secret.yaml
```

Удалить monitoring stack при необходимости:

```bash
helm uninstall prometheus -n monitoring
```

Остановить Minikube:

```bash
minikube stop
```