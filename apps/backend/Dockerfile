FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./gradlew :apps:backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY --from=build /workspace/apps/backend/build/libs/backend-0.1.0.jar /srv/app.jar
COPY packages/codegen /srv/codegen

ENV IKK_CODEGEN_SCRIPT=/srv/codegen/generate.mjs
ENV IKK_BIND_ADDRESS=0.0.0.0
EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/srv/app.jar"]
