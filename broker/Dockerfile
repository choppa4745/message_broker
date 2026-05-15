FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY broker/pom.xml broker/pom.xml
COPY sdk/pom.xml sdk/pom.xml
COPY broker/src broker/src
RUN mvn -q -pl broker -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/broker/target/pigeonmq.jar /app/pigeonmq.jar
EXPOSE 1883 8080
ENTRYPOINT ["java", "-jar", "pigeonmq.jar"]
