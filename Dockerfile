LABEL authors="hesamdavarpanah"
FROM eclipse-temurin:27-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Dmaven.test.skip=true clean package \
    && cp target/service-balance-*.jar /workspace/app.jar

FROM eclipse-temurin:27-jre

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]