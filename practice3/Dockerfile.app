FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /work
COPY pom.xml pom.xml
COPY application/pom.xml application/pom.xml
COPY load-generator/pom.xml load-generator/pom.xml
COPY application/src application/src
RUN mvn -q -pl application -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update -qq && apt-get install -y -qq curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /work/application/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

