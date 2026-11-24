# ---- Build stage: JDK + Maven wrapper, produces the Spring Boot jar ----
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

# Maven Central answers HTTP 429 to shared build hosts (e.g. Render), so both the Maven
# distribution (MVNW_REPOURL) and the dependencies (mirror-settings.xml) come from
# Google's official Maven Central mirror.
ENV MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2

# Download dependencies in their own layer, so it stays cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -s .mvn/mirror-settings.xml dependency:go-offline

COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN ./mvnw -B -q -s .mvn/mirror-settings.xml package -DskipTests

# ---- Run stage: JRE only, as a non-root user ----
FROM eclipse-temurin:26-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
# Free hosting plans have about 512 MB of memory: keep the heap to a share of the container's
# limit, use the small serial GC, and restart cleanly instead of limping on after an OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
