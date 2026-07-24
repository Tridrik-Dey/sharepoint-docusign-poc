# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/sharepoint-docusign-poc.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Mock mode needs no credentials; override with SPRING_PROFILES_ACTIVE=local
# plus the variables in .env.example to talk to real Microsoft Graph/DocuSign.
ENV SPRING_PROFILES_ACTIVE=mock
EXPOSE 8080

# entrypoint.sh optionally materializes DOCUSIGN_PRIVATE_KEY_BASE64 into a file
# before starting the JVM - see that script for details (needed on PaaS hosts
# with no local filesystem to point DOCUSIGN_PRIVATE_KEY_PATH at).
ENTRYPOINT ["/app/entrypoint.sh"]
