FROM openjdk:21-jdk

COPY target/payment-service-*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
