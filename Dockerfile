# ===========================================
# mailctl - Lightweight Mail Server Control Plane
# Multi-stage build for the Java CLI
# Base: Eclipse Temurin JDK 25 (Debian based)
#
# DEPLOY WORKFLOW (from the host):
#   bash scripts/update.sh
#
# This image ships the mailctl CLI only. Postfix, Dovecot, Rspamd and
# Postgres run as sibling containers (see docker-compose.yml). Commands
# are run with:
#   docker compose exec mailctl mailctl domain list
#
# Database routing is chosen by MAILCTL_DB_MODE:
#   local     -> the bundled postgres container, no TLS, unpublished port
#   external  -> a remote host over sslmode=verify-full + channelBinding=require
# The entrypoint refuses to start if the mode and the TLS settings disagree.
# ===========================================

ARG JDK_VENDOR=eclipse-temurin
ARG JDK_VERSION=25
ARG BUILDER_VENDOR=maven
ARG BUILDER_VERSION=3.9.15

# Stage 1: Build
# Uses `mvn` from the builder image directly. There is no mvnw wrapper in
# this repo, so a ./mvnw build would fail at COPY.
FROM ${BUILDER_VENDOR}:${BUILDER_VERSION}-${JDK_VENDOR}-${JDK_VERSION} AS builder

WORKDIR /app

# Copy source (always builds from the local checkout)
COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests

# Stage 2: Runtime (lean - no Maven, no Git)
# NOTE: the previous Dockerfile used this same Maven image as the runtime
# and then ran `apk add`, which fails because that image is Debian based
# and has no apk. This stage is a plain JRE plus the mail client tools.
FROM ${JDK_VENDOR}:${JDK_VERSION}-jre

LABEL maintainer="World Standard Group"
LABEL description="mailctl - Lightweight Mail Server Control Plane"
LABEL version="0.0.1-indev"

# postfix/dovecot-core/rspamd are installed for their CLIENT binaries only
# (postfix, postmap, postqueue, doveadm, rspamadm). Without them
# `config validate`, `config deploy` and the doctor service checks cannot run at
# all: they shell out to these tools. No daemon is started in this image.
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    postgresql-client \
    bash \
    coreutils \
    findutils \
    postfix \
    postfix-pcre \
    dovecot-core \
    rspamd \
    && rm -rf /var/lib/apt/lists/* \
    # dpkg tries to start these daemons at install time; there is no init in a
    # container, and a stray listener would be confusing.
    && mkdir -p /usr/sbin/policy-rc.d \
    && printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d \
    && chmod +x /usr/sbin/policy-rc.d \
    && rm -f /usr/sbin/policy-rc.d

WORKDIR /app

# The shade plugin emits a single fat jar named lw-mailserver-<version>.jar
COPY --from=builder /app/target/lw-mailserver-*.jar /app/mailctl.jar

# Flyway migrations live inside the jar (src/main/resources/db/migration),
# so there is no separate db/migration copy step.

ENV MAILCTL_CONFIG=/etc/mailctl/config.yaml
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC"

# Mailbox, generated-config and backup locations. These are bind-mounted
# from the host so generated configuration survives a container rebuild.
ENV MAILCTL_MAIL_BASE=/var/mail \
    MAILCTL_POSTFIX_DIR=/etc/mailctl/generated/postfix \
    MAILCTL_DOVECOT_DIR=/etc/mailctl/generated/dovecot \
    MAILCTL_RSPAMD_DIR=/etc/mailctl/generated/rspamd \
    MAILCTL_DKIM_DIR=/etc/mailctl/generated/dkim \
    MAILCTL_BACKUP_DIR=/var/backups/mailctl

# /etc/mailctl/pki is container-owned and writable so the entrypoint can stage
# the client key at mode 0600: PgJDBC refuses a group/world-readable key, and
# the keys volume is mounted read-only (0444).
RUN mkdir -p /etc/mailctl/generated/postfix /etc/mailctl/generated/dovecot \
             /etc/mailctl/generated/rspamd /etc/mailctl/generated/dkim \
             /etc/mailctl/pki /var/mail /var/backups/mailctl \
    && chmod 700 /var/mail /etc/mailctl/pki

COPY scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Idles by default so `docker compose exec mailctl mailctl ...` works.
# The entrypoint applies migrations on start, then idles.
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["sleep", "infinity"]
