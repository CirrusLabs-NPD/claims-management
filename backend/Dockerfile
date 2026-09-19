# syntax=docker/dockerfile:1
# ---------- build ----------
# Everything compiles inside the image: no local JDK or Maven needed.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first so the layer caches across source-only changes.
COPY pom.xml .
# Cache the local repo across builds (keyed by pom.xml layer) so a pom.xml
# change doesn't force re-downloading the whole dependency tree from scratch.
# Pure cache warm-up. go-offline cannot always resolve every plugin, and that is
# not a build failure: the package step below fetches whatever is still missing.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q dependency:go-offline \
    || echo "dependency pre-fetch incomplete; package will fetch the remainder"

COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run the JVM as root.
RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/target/claim-management.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 9090

# Containers get a memory limit, not a machine. Let the JVM read the cgroup.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD wget -qO- http://127.0.0.1:9090/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
