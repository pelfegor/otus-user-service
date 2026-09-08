FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY src src

RUN chmod +x gradlew \
    && ./gradlew clean bootJar --no-daemon


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup --system application \
    && adduser --system --ingroup application application

COPY --from=builder \
    --chown=application:application \
    /workspace/build/libs/user-service-1.0.0.jar \
    application.jar

USER application

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/application.jar"]