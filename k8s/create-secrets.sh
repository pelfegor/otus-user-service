#!/usr/bin/env sh
set -eu

if kubectl get secret user-service-db-secret --namespace m >/dev/null 2>&1; then
  echo "Keeping existing user-service-db-secret in namespace m"
else
  : "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD before creating the database Secret}"
  kubectl create secret generic user-service-db-secret \
    --namespace m \
    --from-literal=postgres-password="$POSTGRES_PASSWORD" \
    --from-literal=password="$POSTGRES_PASSWORD" \
    --from-literal=DB_USERNAME="${DB_USERNAME:-app_user}" \
    --from-literal=DB_PASSWORD="$POSTGRES_PASSWORD"
fi

if kubectl get secret user-service-jwt-secret --namespace m >/dev/null 2>&1; then
  echo "Keeping existing user-service-jwt-secret in namespace m"
else
  : "${JWT_SECRET:?Set JWT_SECRET to a random value of at least 32 characters}"
  if [ "${#JWT_SECRET}" -lt 32 ]; then
    echo "JWT_SECRET must contain at least 32 characters" >&2
    exit 1
  fi
  kubectl create secret generic user-service-jwt-secret \
    --namespace m \
    --from-literal=JWT_SECRET="$JWT_SECRET"
fi
