# User Service

RESTful CRUD-сервис для управления пользователями, развернутый в Kubernetes с PostgreSQL, мониторингом через Prometheus/Grafana и централизованным логированием на базе Elasticsearch, Fluent Bit и Kibana.

Проект выполнен в рамках домашних заданий OTUS по микросервисной архитектуре.

---

# Функциональность

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

---

# Стек

## Application

- Java 21
- Spring Boot 3.5
- Spring Data JDBC
- Spring Boot Actuator
- Micrometer
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

- Postman
- Newman

---

# Архитектура

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
                   │User Service │           │User Service │
                   │    Pod 1    │           │    Pod 2    │
                   └──────┬──────┘           └──────┬──────┘
                          │                         │
                          └────────────┬────────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │    PostgreSQL    │
                              └──────────────────┘
```

## Monitoring

```text
User Service ─────┐
                  │
                  ▼
             ┌────────────┐
             │ Prometheus │
             └─────┬──────┘
                   │
                   ▼
             ┌────────────┐
             │  Grafana   │
             └────────────┘

NGINX Ingress ────► Prometheus
```

Prometheus собирает метрики User Service и NGINX Ingress Controller.

Grafana используется для визуализации метрик и alerting.

## Централизованное логирование

```text
┌────────────────────┐
│    User Service    │
│      stdout        │
└─────────┬──────────┘
          │
          ▼
 /var/log/containers/*.log
          │
          ▼
┌────────────────────┐
│     Fluent Bit     │
│     DaemonSet      │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│   Elasticsearch    │
│ fluent-bit-logs-*  │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│       Kibana       │
│ Discover / Charts  │
└────────────────────┘
```

Fluent Bit собирает container logs с Kubernetes node и отправляет их в Elasticsearch.

Kibana используется для поиска, фильтрации и визуализации логов.

Конфигурация приложения хранится в Kubernetes `ConfigMap`.

Данные для подключения к PostgreSQL хранятся в Kubernetes `Secret`.

Первоначальная миграция базы данных выполняется отдельным Kubernetes `Job`.

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

Запустить Minikube:

```bash
minikube start --driver=docker
```

Проверить состояние:

```bash
kubectl get nodes
```

---

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

---

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

---

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

Job должен иметь статус:

```text
Complete
```

Migration Job пишет структурированные JSON-логи начала и окончания миграции.

Пример:

```json
{
  "level": "INFO",
  "service": "user-service-migration",
  "message": "Database migration started"
}
```

```json
{
  "level": "INFO",
  "service": "user-service-migration",
  "message": "Database migration completed"
}
```

---

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

Проверить используемый Docker image:

```bash
kubectl get pods -l app=user-service \
  -o jsonpath='{range .items[*]}{.metadata.name}{" -> "}{.spec.containers[0].image}{"\n"}{end}'
```

---

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

---

## Получение пользователя

```bash
curl http://arch.homework/users/1
```

---

## Получение списка пользователей

```bash
curl http://arch.homework/users
```

---

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

---

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

---

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

Для локального доступа к Grafana:

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

## User Service

- `User Service — RPS by API`
- `User Service — Latency by API`
- `User Service — 500 Error Rate by API`

Метрики группируются по API и HTTP method.

## NGINX Ingress

- `NGINX Ingress — RPS`
- `NGINX Ingress — Latency`
- `NGINX Ingress — 500 Error Rate`

Таким образом можно отдельно наблюдать поведение самого приложения и входящего трафика через Ingress.

### RPS приложения

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

### HTTP 500 приложения

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

### NGINX Ingress RPS

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

# Централизованное логирование

Для централизованного сбора и анализа логов используется стек EFK:

- Elasticsearch — хранение и поиск логов;
- Fluent Bit — сбор и доставка логов Kubernetes;
- Kibana — поиск, фильтрация и визуализация.

---

## Structured logging

User Service пишет структурированные JSON-логи в `stdout`.

Для этого используется встроенная поддержка structured logging Spring Boot.

Конфигурация:

```yaml
logging:
  structured:
    format:
      console: logstash
```

Логируются:

- запуск приложения — `INFO`;
- остановка приложения — `INFO`;
- входящие HTTP-запросы — `INFO`;
- успешное создание пользователя — `INFO`;
- успешное обновление пользователя — `INFO`;
- успешное удаление пользователя — `INFO`;
- ошибки валидации — `WARN`;
- попытки обращения к отсутствующему пользователю — `WARN`;
- конфликты уникальных значений — `WARN`;
- ошибки доступа к БД — `ERROR`;
- непредвиденные ошибки приложения — `ERROR`.

Spring Boot 3.5 предоставляет встроенную поддержку structured logging для console output.

---

## Request ID

Для каждого входящего HTTP-запроса генерируется уникальный:

```text
request_id
```

`request_id` помещается в MDC и автоматически присутствует в логах, созданных во время обработки запроса.

Пример:

```json
{
  "@timestamp": "2026-09-28T18:14:33.219858346Z",
  "@version": "1",
  "message": "Incoming HTTP request",
  "level": "INFO",
  "request_id": "ae0579c2-b4b9-4ce4-aae4-62beba786e77"
}
```

Это позволяет найти все логи, относящиеся к одному HTTP-запросу.

---

## HTTP request logging

Для входящих HTTP-запросов логируются:

- HTTP method;
- request path;
- client IP;
- request ID.

Пример сообщения:

```text
Incoming HTTP request
```

Дополнительные данные запроса передаются в structured log.

---

## CRUD logging

Успешные изменяющие CRUD-операции логируются на уровне `INFO`.

Для идентификатора пользователя используется отдельное структурированное поле:

```text
user_id
```

Пример:

```json
{
  "message": "User created",
  "level": "INFO",
  "user_id": 12748
}
```

Аналогично логируются:

```text
User updated
User deleted
```

Это позволяет искать операции конкретного пользователя непосредственно по полю `user_id`, не разбирая текст сообщения.

---

# Migration Job logging

Kubernetes migration Job также пишет структурированные JSON-логи в stdout.

Лог начала миграции:

```json
{
  "level": "INFO",
  "service": "user-service-migration",
  "message": "Database migration started"
}
```

Лог успешного окончания:

```json
{
  "level": "INFO",
  "service": "user-service-migration",
  "message": "Database migration completed"
}
```

Migration Job можно найти в Kibana по:

```text
service: "user-service-migration"
```

---

# Elasticsearch

Elasticsearch развернут в namespace:

```text
logging
```

Используется:

- StatefulSet;
- single-node configuration;
- PersistentVolumeClaim;
- Headless Service;
- ClusterIP Service.

Проверить:

```bash
kubectl get pods -n logging
kubectl get svc -n logging
kubectl get pvc -n logging
```

Для локального доступа:

```bash
kubectl port-forward -n logging svc/elasticsearch 9200:9200
```

Проверить Elasticsearch:

```bash
curl http://localhost:9200
```

Проверить индексы Fluent Bit:

```bash
curl 'http://localhost:9200/_cat/indices/fluent-bit-logs-*?v'
```

Логи хранятся в daily indices:

```text
fluent-bit-logs-YYYY.MM.DD
```

Например:

```text
fluent-bit-logs-2026.09.28
```

---

# Fluent Bit

Fluent Bit развернут как Kubernetes `DaemonSet`.

Проверить:

```bash
kubectl get daemonset -n logging
kubectl get pods -n logging -l app=fluent-bit
```

Fluent Bit читает container logs с Kubernetes node:

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

Пример Kubernetes metadata:

```json
{
  "kubernetes": {
    "pod_name": "user-service-844d47ffd-sknjr",
    "namespace_name": "default",
    "container_name": "user-service",
    "labels": {
      "app": "user-service"
    }
  }
}
```

JSON-логи приложения объединяются с Kubernetes metadata и отправляются в Elasticsearch.

Elasticsearch output настроен с:

```text
Logstash_Format On
Logstash_Prefix fluent-bit-logs
```

Проверить логи Fluent Bit:

```bash
kubectl logs -n logging -l app=fluent-bit
```

---

# Kibana

Kibana развернута в namespace:

```text
logging
```

Для локального доступа:

```bash
kubectl port-forward -n logging svc/kibana 5601:5601
```

После этого Kibana доступна на:

```text
http://localhost:5601
```

---

## Data View

Для просмотра логов создан Data View:

```text
Fluent Bit Logs
```

Index pattern:

```text
fluent-bit-logs-*
```

Time field:

```text
@timestamp
```

---

## Поиск логов User Service

В Kibana Discover:

```text
kubernetes.container_name: "user-service"
```

Отображаются централизованные логи обеих реплик приложения.

---

## Поиск WARN

Для поиска ошибок валидации:

```text
kubernetes.container_name: "user-service" and level: "WARN"
```

Пример сообщения:

```text
Request validation failed
```

Лог содержит:

- `level`;
- `message`;
- `request_id`;
- Kubernetes metadata.

---

## Поиск CRUD по user_id

Для поиска операции конкретного пользователя:

```text
kubernetes.container_name: "user-service" and user_id: 12748
```

Пример результата:

```text
level      INFO
message    User created
user_id    12748
request_id c3e7533e-8ae1-420b-a035-d0cb8f283a49
```

Поле `user_id` хранится отдельно от `message`, что позволяет использовать его для поиска и фильтрации.

---

## Поиск migration Job

```text
service: "user-service-migration"
```

В результате отображаются:

```text
Database migration started
Database migration completed
```

---

## Histogram по уровню логирования

Для анализа количества логов во времени используется histogram.

Фильтр:

```text
kubernetes.container_name: "user-service"
```

Breakdown:

```text
level.keyword
```

Это позволяет отдельно отображать количество логов уровней:

```text
INFO
WARN
ERROR
```

при их наличии за выбранный временной диапазон.

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

## Основные ресурсы приложения

- `01-secret.yaml` — credentials PostgreSQL;
- `02-configmap.yaml` — конфигурация User Service;
- `03-migration-configmap.yaml` — SQL первоначальной миграции;
- `04-migration-job.yaml` — Kubernetes Job для выполнения миграции;
- `05-deployment.yaml` — Deployment приложения с двумя репликами;
- `06-service.yaml` — ClusterIP Service;
- `07-ingress.yaml` — Ingress для `arch.homework`;
- `postgres-values.yaml` — values для PostgreSQL Helm chart.

## Centralized logging

Ресурсы централизованного логирования находятся в:

```text
k8s/logging/
```

Они разворачивают:

- namespace `logging`;
- Elasticsearch StatefulSet;
- PersistentVolumeClaim для Elasticsearch;
- Elasticsearch Headless Service;
- Elasticsearch ClusterIP Service;
- Kibana Deployment;
- Kibana Service;
- ServiceAccount для Fluent Bit;
- RBAC для доступа Fluent Bit к Kubernetes metadata;
- Fluent Bit ConfigMap;
- Fluent Bit DaemonSet.

---

# Docker

Docker image:

```text
pelfegor/user-service:1.2.0
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
  -t pelfegor/user-service:1.2.0 \
  --push .
```

Проверить используемый Kubernetes image:

```bash
kubectl get pods -l app=user-service \
  -o jsonpath='{range .items[*]}{.metadata.name}{" -> "}{.spec.containers[0].image}{"\n"}{end}'
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

# Проверка централизованного логирования

## Проверить компоненты EFK

```bash
kubectl get pods -n logging
kubectl get svc -n logging
kubectl get daemonset -n logging
kubectl get statefulset -n logging
kubectl get pvc -n logging
```

## Проверить Elasticsearch

Запустить:

```bash
kubectl port-forward -n logging svc/elasticsearch 9200:9200
```

В другом терминале:

```bash
curl http://localhost:9200
```

Проверить индексы:

```bash
curl 'http://localhost:9200/_cat/indices/fluent-bit-logs-*?v'
```

## Проверить Kibana

```bash
kubectl port-forward -n logging svc/kibana 5601:5601
```

Открыть:

```text
http://localhost:5601
```

## Сгенерировать INFO лог

```bash
curl http://arch.homework/users
```

## Сгенерировать WARN лог

```bash
curl -X POST http://arch.homework/users \
  -H "Content-Type: application/json" \
  -d '{}'
```

После этого в Kibana Discover можно выполнить:

```text
kubernetes.container_name: "user-service" and level: "WARN"
```

## Сгенерировать CRUD лог с user_id

```bash
curl -X POST http://arch.homework/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "kibana-test",
    "firstName": "Kibana",
    "lastName": "Test",
    "email": "kibana-test@example.com",
    "phone": "+79991112233"
  }'
```

Полученный `id` можно использовать для поиска:

```text
kubernetes.container_name: "user-service" and user_id: <USER_ID>
```

---

# Удаление ресурсов

## Удалить приложение

```bash
kubectl delete -f k8s/07-ingress.yaml
kubectl delete -f k8s/06-service.yaml
kubectl delete -f k8s/05-deployment.yaml
kubectl delete -f k8s/04-migration-job.yaml
kubectl delete -f k8s/03-migration-configmap.yaml
kubectl delete -f k8s/02-configmap.yaml
```

## Удалить PostgreSQL

```bash
helm uninstall user-service-postgres
```

Удалить Secret:

```bash
kubectl delete -f k8s/01-secret.yaml
```

## Удалить centralized logging

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

## Удалить monitoring stack

При необходимости:

```bash
helm uninstall prometheus -n monitoring
```

## Остановить Minikube

```bash
minikube stop
```