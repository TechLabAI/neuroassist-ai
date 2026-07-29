FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . /workspace
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
