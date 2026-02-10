# syntax=docker/dockerfile:1.6

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . /app
RUN ./mvnw -q -DskipTests package

FROM python:3.11-slim
WORKDIR /app

# System deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

# Copy app
COPY --from=build /app/target/prayer-bot-1.0.0-MVP.jar /app/target/prayer-bot-1.0.0-MVP.jar
COPY listener /app/listener

# Python deps
RUN pip install --no-cache-dir -r /app/listener/requirements.txt

ENV PYTHONUNBUFFERED=1

EXPOSE 8080

CMD python3 -m listener.app & java -Dserver.port=$PORT $JAVA_OPTS -jar /app/target/prayer-bot-1.0.0-MVP.jar
