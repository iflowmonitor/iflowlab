# iflowlab runner image (SaaS R2, iflowlab-cloud ADR-0002/0006).
# The same jar the local product ships, booted with QUARKUS_PROFILE=runner:
# a stateless execution service for a control plane. Byte-identical across
# SaaS and self-install; parameterized only by env vars.

# --- build: maven + JDK 21 + Node 20 (Quinoa builds the SPA into the jar) ---
FROM maven:3.9-eclipse-temurin-21 AS build
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY pom.xml .
COPY cpi-mock/pom.xml cpi-mock/
COPY engine/pom.xml engine/
COPY app/pom.xml app/
RUN mvn -B -q dependency:go-offline -pl app -am || true
COPY cpi-mock cpi-mock
COPY engine engine
COPY app app
COPY LICENSE NOTICE ./
RUN mvn -B -q package -DskipTests

# --- runtime: JRE only, non-root ---
FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source="https://github.com/iflowmonitor/iflowlab" \
      org.opencontainers.image.description="iflowlab runner — stateless SAP CPI script execution service" \
      org.opencontainers.image.licenses="Apache-2.0"
WORKDIR /app
COPY --from=build /src/app/target/quarkus-app/ /app/
COPY --from=build /src/LICENSE /src/NOTICE /app/
ENV QUARKUS_PROFILE=runner
# IFLOWLAB_RUNNER_TOKEN: shared secret the control plane must present (X-Runner-Token).
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
