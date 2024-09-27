FROM gradle:8.10-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# RUN ls -la /home/gradle/src && gradle -Pksp.useKSP2=true clean :shadowJar --no-daemon
RUN gradle :shadowJar
# RUN ./gradlew clean :shadowJar

FROM amazoncorretto:21 AS runtime

RUN mkdir /app

# RUN addgroup -S user -g 1000 && \
#     useradd -S user -G user --uid 1000
# USER user:user

COPY --from=build --chown=user:user /home/gradle/src/build/libs/application.jar /app/application.jar

ENV DOCKER_LOGGING="true"
ENV JVM_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT java $JVM_OPTS -jar /app/application.jar
