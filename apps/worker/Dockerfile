FROM maven:3.9.15-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S -g 10001 openecosystem && adduser -S -D -H -u 10001 -G openecosystem openecosystem
COPY --from=builder /app/target/open-ecosystem-worker-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
