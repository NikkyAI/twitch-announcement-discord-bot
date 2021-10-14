FROM openjdk:17-alpine AS runtime

RUN apk --no-cache add bash
RUN mkdir /app

COPY build/libs/application.jar /application.jar

ENV JVM_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT java $JVM_OPTS -jar /application.jar
