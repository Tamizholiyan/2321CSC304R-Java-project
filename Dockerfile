# ==========================================
# STAGE 1: Build Application
# ==========================================
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy Maven wrapper and POM first to leverage Docker layer caching for dependencies
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Normalize line endings and permissions for Linux container execution
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies offline (cached unless pom.xml changes)
RUN ./mvnw -B dependency:go-offline

# Copy source code and build runnable artifact
COPY src src
RUN ./mvnw -B -DskipTests package

# ==========================================
# STAGE 2: Lightweight Runtime Image
# ==========================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy runnable fat JAR from build stage (excluding *.jar.original)
COPY --from=build /app/target/campuscommute-0.0.1-SNAPSHOT.jar app.jar

# JVM Performance & Memory Tuning Flags for Constrained Free-Tier Environments (512 MB RAM, 0.1 CPU):
# -XX:MaxRAMPercentage=65: Caps heap memory at 65% (~330 MB) of container RAM, reserving buffer for OS, metaspace & thread stacks to avoid OOM kills.
# -XX:+UseSerialGC: Lightweight single-threaded garbage collector with minimal CPU/RAM overhead for single/fractional vCPU environments.
# -XX:TieredStopAtLevel=1: Restricts JIT to C1 client compiler, drastically speeding up container boot time and reducing compiler memory.
# -Xss512k: Halves per-thread stack size from 1024k to 512k, conserving memory across Tomcat worker threads.
# -Duser.timezone=Asia/Kolkata: Enforces Indian Standard Time (IST, UTC+5:30) across all timestamp operations.
# -jar app.jar: Launches the Spring Boot application.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss512k", "-Duser.timezone=Asia/Kolkata", "-jar", "app.jar"]
