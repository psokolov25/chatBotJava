FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/telegram-orchestra-bot-*.jar /app/app.jar
COPY client_path.yml /app/client_path.yml
COPY src/main/resources/application.yml /app/application.yml
ENV MICRONAUT_CONFIG_FILES=/app/application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
