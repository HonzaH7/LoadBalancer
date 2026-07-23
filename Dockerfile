# ---- Stage 1: build the app with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only the pom first, download dependencies, THEN copy source.
# This way Docker caches the dependency layer and only re-downloads
# when pom.xml actually changes (not on every code edit).
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---- Stage 2: slim runtime image ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy only the built jar from the build stage. Maven and the source
# code stay behind in stage 1 and never reach the final image.
COPY --from=build /app/target/LoadBalancer-1.0-SNAPSHOT.jar app.jar

# ENTRYPOINT is the fixed part of the command, CMD is the default
# argument. That lets us reuse ONE image for both roles: the default
# runs the load balancer, but a backend container overrides CMD with
# loadbalancer.BackendMain.
ENTRYPOINT ["java", "-cp", "app.jar"]
CMD ["loadbalancer.LbMain"]
