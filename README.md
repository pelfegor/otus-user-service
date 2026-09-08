# User Service

RESTful CRUD-сервис для управления пользователями.

Проект выполнен в рамках домашнего задания OTUS «Инфраструктурные паттерны».

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

## Стек

- Java 21
- Spring Boot 3.5
- Spring Data JDBC
- PostgreSQL
- Gradle
- Docker
- Kubernetes
- Helm
- NGINX Ingress Controller
- Postman / Newman

## Структура Kubernetes

Приложение разворачивается по следующей схеме:

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
```

Конфигурация приложения хранится в `ConfigMap`.

Данные для подключения к PostgreSQL хранятся в Kubernetes `Secret`.

Первоначальная миграция базы данных выполняется отдельным Kubernetes `Job`.

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

В кластере должен быть установлен NGINX Ingress Controller.

Пример установки через Helm:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install nginx-ingress ingress-nginx/ingress-nginx \
  --namespace m \
  --create-namespace
```

Проверка:

```bash
kubectl get pods -n m
```

## 3. Установка PostgreSQL

Добавить Helm-репозиторий Bitnami:

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

Сначала создать Secret с данными подключения:

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

## 4. Выполнение первоначальной миграции

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

Проверить статус:

```bash
kubectl get jobs
```

Посмотреть лог миграции:

```bash
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

Проверить состояние:

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

## 6. Настройка доступа к Ingress

Добавить в `/etc/hosts`:

```text
127.0.0.1 arch.homework
```

Запустить Minikube tunnel в отдельном терминале:

```bash
minikube tunnel
```

Проверить health endpoint:

```bash
curl http://arch.homework/health/
```

Ожидаемый ответ:

```json
{
  "status": "OK"
}
```

Проверить API пользователей:

```bash
curl http://arch.homework/users
```

## 7. CRUD API

### Создание пользователя

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

### Получение пользователя

```bash
curl http://arch.homework/users/1
```

### Обновление пользователя

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

### Удаление пользователя

```bash
curl -X DELETE http://arch.homework/users/1
```

Успешный ответ:

```text
HTTP 204 No Content
```

## 8. Postman / Newman

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

Запустить коллекцию через Newman:

```bash
npx newman run postman/user-service.postman_collection.json
```

При успешном выполнении все четыре запроса и все assertions должны завершиться без ошибок:

```text
iterations        1   failed 0
requests          4   failed 0
test-scripts      4   failed 0
assertions        7   failed 0
```

## 9. Kubernetes manifests

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

## 10. Docker

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

## 11. Удаление ресурсов

Удалить ресурсы приложения:

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

При необходимости полностью остановить Minikube:

```bash
minikube stop
```