ARG JDK_VENDOR=eclipse-temurin
ARG JDK_VERSION=25
ARG BUILDER_VENDOR=maven
ARG BUILDER_VERSION=3.9.15

FROM ${BUILDER_VENDOR}:${BUILDER_VERSION}-${JDK_VENDOR}-${JDK_VERSION} AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY .mvn .mvn
COPY mvnw .

RUN ./mvnw package -DskipTests -q

FROM ${BUILDER_VENDOR}:${BUILDER_VERSION}-${JDK_VENDOR}-${JDK_VERSION}

RUN apk add --no-cache \
    postgresql-client \
    bash \
    coreutils \
    findutils

WORKDIR /app

COPY --from=builder /app/target/lw-mailserver-0.0.1-indev.jar mailctl.jar

ENV MAILCTL_CONFIG=/etc/mailctl/config.yaml
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["java", "-jar", "mailctl.jar"]