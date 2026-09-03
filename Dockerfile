FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# go-offline pulls the entire transitive graph and often OOMs on small Coolify builders.
ENV MAVEN_OPTS="-Xmx768m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B dependency:resolve dependency:resolve-plugins -DskipTests

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B clean package -DskipTests && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /app/credentials /app/uploads

COPY --from=build /app/app.jar ./app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
