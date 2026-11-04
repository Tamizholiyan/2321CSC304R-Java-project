# ---- Build stage: this Maven image already contains Maven, so no mvnw or .mvn is needed ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Runtime stage: JRE only, so the final image is small ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage: stay inside Render's 512 MB limit. SerialGC and TieredStopAtLevel=1: lighter on the 0.1 CPU free instance.
# Xss: smaller thread stacks. user.timezone: Render runs in UTC, but your ride times are in IST.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-XX:+UseSerialGC","-XX:TieredStopAtLevel=1","-Xss512k","-Duser.timezone=Asia/Kolkata","-jar","app.jar"]