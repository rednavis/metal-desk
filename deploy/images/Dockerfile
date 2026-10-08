# One image build for all three JVM services; they differ only in which Spring Boot jar they run.
#
#   docker build --build-arg MODULE=services/api            -f deploy/images/Dockerfile -t metaldesk-api .
#   docker build --build-arg MODULE=services/pricing-bridge -f deploy/images/Dockerfile -t metaldesk-pricing-bridge .
#   docker build --build-arg MODULE=apps/admin              -f deploy/images/Dockerfile -t metaldesk-admin .
#
# Run it from the repository root (the build context). MODULE is the module's directory; the Gradle project path is
# derived from it. Nothing sensitive is a build argument: build arguments are recorded in the image history for
# good. Secrets arrive as environment variables at deploy time (Secret Manager, T-075), never in the image.
#
# BASE IMAGES. The two Temurin tags below are exact releases and are the only place the base-image version is
# written (a second place a version lives, next to gradle/libs.versions.toml, which governs Gradle dependencies only).
# Dependabot's docker ecosystem watches this file (.github/dependabot.yml). Bump the JDK and JRE tags together.

# --- build: compile the module's executable jar from source ------------------------------------------------------
# Self-contained on purpose: one `docker build` from a clean checkout yields the image, with no step that has to run
# first and no jar that can be stale. The price is a slower build, softened by BuildKit's Gradle cache mount.
FROM eclipse-temurin:26.0.2.1_1-jdk-noble AS build

ARG MODULE
WORKDIR /src
COPY . .

# `-x test`: the image build compiles and packages; tests and the quality gates run in CI's JVM build.
RUN --mount=type=cache,target=/root/.gradle \
    set -eu; \
    test -n "${MODULE}" || { echo "MODULE is required, for example --build-arg MODULE=services/api" >&2; exit 2; }; \
    project=":$(echo "${MODULE}" | tr / :)"; \
    ./gradlew "${project}:bootJar" -x test --no-daemon --console=plain; \
    jar="$(ls "${MODULE}"/build/libs/*.jar | grep -v -- '-plain\.jar$')"; \
    test "$(echo "${jar}" | wc -l)" -eq 1; \
    cp "${jar}" /application.jar

# --- context: what the build context actually contains (not part of the image) ----------------------------------
# `docker build --target build-context --output type=local,dest=./ctx -f deploy/images/Dockerfile .` exports it, which is
# how the build-context filter (Dockerfile.dockerignore) is verified.
FROM scratch AS build-context
COPY . /

# --- runtime: a JRE, a non-root user, and java as PID 1 --------------------------------------------------------------
FROM eclipse-temurin:26.0.2.1_1-jre-noble AS runtime

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

# WORKDIR first, so /app is created with normal permissions (a --chmod on COPY would also apply to a directory it
# creates, and a directory without the execute bit cannot be entered). The jar is owned by root and read-only: the
# application user can run it but cannot change it.
WORKDIR /app
COPY --from=build --chown=0:0 --chmod=444 /application.jar /app/application.jar

USER 10001:10001

# The heap follows the container's memory limit, not the host's, and an out-of-memory error ends the process so Cloud
# Run replaces the instance instead of leaving a broken one serving. Override at deploy time if needed.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Cloud Run sends SIGTERM on scale-down and allows about ten seconds. Spring Boot stops at once by default and would
# drop in-flight requests (on the checkout path, a payment in an unknown state), so ask for a graceful shutdown that
# waits for them, bounded below Cloud Run's limit. Relaxed binding maps these to server.shutdown and
# spring.lifecycle.timeout-per-shutdown-phase, so the applications need no change.
ENV SERVER_SHUTDOWN=graceful \
    SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=8s

# Exec form, so `java` is PID 1 and receives SIGTERM itself rather than a shell that swallows it.
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
