#
# Build stage
#
FROM maven:3.8.4-openjdk-17 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

FROM openjdk:17
ARG version=-1.0.0
COPY --from=build /home/app/target/wordle-solver${version}.jar /wordle-solver.jar
WORKDIR /
CMD java -jar wordle-solver.jar
