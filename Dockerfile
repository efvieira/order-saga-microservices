# syntax=docker/dockerfile:1
# Uma imagem por serviço: docker build --build-arg SERVICE=order-service .
FROM maven:3.9-eclipse-temurin-25 AS build
ARG SERVICE
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl ${SERVICE} -am package -DskipTests \
    && java -Djarmode=tools -jar ${SERVICE}/target/${SERVICE}-*.jar extract --layers --launcher --destination /app/extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
