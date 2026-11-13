# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY . .
# Windows checkouts may have CRLF scripts and no executable bit.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :sky-job:bootJar :sky-plugin-runner-java:jar \
    && mkdir /out \
    && cp sky-job/build/libs/sky-job-1.0-SNAPSHOT.jar /out/app.jar \
    && cp sky-plugin-runner-java/build/libs/sky-plugin-runner-java-1.0-SNAPSHOT.jar /out/runner.jar

FROM eclipse-temurin:25-jdk-noble AS runtime
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo Asia/Shanghai > /etc/timezone \
    && groupadd --gid 10001 sky \
    && useradd --uid 10001 --gid sky --no-create-home --home-dir /app sky \
    && mkdir -p /app/data/plugins \
    && chown -R sky:sky /app
WORKDIR /app
COPY --from=build --chown=sky:sky /out/ /app/
ENV TZ=Asia/Shanghai \
    SKY_PLUGIN_JAVA_COMMAND=/opt/java/openjdk/bin/java \
    SKY_PLUGIN_RUNNER_JAR=/app/runner.jar \
    SKY_PLUGIN_STORAGE_ROOT=/app/data/plugins \
    SKY_PLUGIN_ARTIFACT_ROOT=/app/data/plugins/artifacts \
    SKY_PLUGIN_RUNTIME_WORK_ROOT=/app/data/plugins/runtime \
    SKY_PLUGIN_RUNTIME_LOG_ROOT=/app/data/plugins/logs \
    SKY_PLUGIN_MAVEN_REPOSITORY_ROOT=/app/data/plugins/maven-repository
USER sky:sky
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/api/actuator/health > /dev/null || exit 1
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/app.jar"]
