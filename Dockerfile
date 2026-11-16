# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/discord-bot-*.jar /bot.jar

FROM eclipse-temurin:25-jre-noble
RUN useradd --system --no-create-home --uid 10001 bot
COPY --from=build /bot.jar /app/bot.jar
USER bot
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/bot.jar"]
