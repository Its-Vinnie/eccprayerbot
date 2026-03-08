# syntax=docker/dockerfile:1.6

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . /app
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/prayer-bot-1.0.0-MVP.jar /app/target/prayer-bot-1.0.0-MVP.jar

EXPOSE 8080

CMD ["/bin/sh", "-c", "java -Dserver.port=$PORT ${JAVA_OPTS} -jar /app/target/prayer-bot-1.0.0-MVP.jar"]
