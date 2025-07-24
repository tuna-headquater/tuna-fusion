FROM maven:3.9.11-eclipse-temurin-21-noble AS builder
ARG MAVEN_SETTING_FILE_URL=https://gist.githubusercontent.com/RobinQu/dcbe33b19f6e09b28d3e7a25630914b7/raw/9596b0608069a0815da93b5331a17e9f61833602/settings.xml
ARG MAVEN_TARGET

WORKDIR /build/
ADD . /build/
ADD $MAVEN_SETTING_FILE_URL /root/.m2/settings.xml
RUN --mount=type=cache,target=/root/.m2 mvn clean package -pl $MAVEN_TARGET -am -DskipTests


FROM eclipse-temurin:21-noble
ARG MAVEN_TARGET
WORKDIR /app
COPY --from=builder /build/$MAVEN_TARGET/target/*.jar /app/app.jar
CMD ["java", "-jar", "app.jar"]
