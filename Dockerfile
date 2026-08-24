FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/job-planner-be-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
