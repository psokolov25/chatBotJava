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
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
ENV MICRONAUT_CONFIG_FILES=/app/application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
