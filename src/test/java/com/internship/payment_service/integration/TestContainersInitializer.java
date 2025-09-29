package com.internship.payment_service.integration;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class TestContainersInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // одни и те же статические контейнеры на весь ран; стартуем их вручную здесь
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.1"));
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    static {
        KAFKA.start();
        MONGO.start();
    }

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        TestPropertyValues.of(
                "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                "spring.data.mongodb.uri=" + MONGO.getConnectionString() + "/payments_db",
                // свои app.*:
                "app.kafka.orders-topic=orders",
                "app.kafka.payments-topic=payments",
                "app.kafka.consumer-group=payment-service-it",
                // страховочные флаги
                "spring.kafka.listener.auto-startup=true",
                "spring.kafka.listener.missing-topics-fatal=false",
                "spring.liquibase.enabled=true"
        ).applyTo(ctx.getEnvironment());
    }
}
