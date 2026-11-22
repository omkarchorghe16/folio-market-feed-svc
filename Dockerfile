FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY target/folio-market-feed-svc-*.jar app.jar
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
