FROM openjdk:17-alpine AS runtime

RUN apk --no-cache add bash
RUN mkdir /app

RUN addgroup -S user -g 1000 && \
    adduser -S user -G user --uid 1000
USER user:user

COPY --chown=user:user build/libs/application.jar /application.jar

ENV JVM_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT java $JVM_OPTS -jar /application.jar
