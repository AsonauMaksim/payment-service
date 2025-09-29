package com.internship.payment_service.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

@SpringBootTest(classes = com.internship.payment_service.PaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
public abstract class BaseIntegrationTest {

    private static final DockerImageName KAFKA_IMAGE =
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"); // можно оставить 7.5.3
    private static final DockerImageName MONGO_IMAGE =
            DockerImageName.parse("mongo:7.0");

    @Container
    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(KAFKA_IMAGE).waitingFor(Wait.forListeningPort());

    @Container
    @ServiceConnection
    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(MONGO_IMAGE).waitingFor(Wait.forListeningPort());

    protected static final String ORDERS_TOPIC = "orders";
    protected static final String PAYMENTS_TOPIC = "payments";
    protected static final String CONSUMER_GROUP = "payment-service-it";

    // Пробрасываем ТОЛЬКО свои app.* (spring.* подставит @ServiceConnection)
    @DynamicPropertySource
    static void appProps(DynamicPropertyRegistry r) {
        r.add("app.kafka.orders-topic", () -> ORDERS_TOPIC);
        r.add("app.kafka.payments-topic", () -> PAYMENTS_TOPIC);
        r.add("app.kafka.consumer-group", () -> CONSUMER_GROUP);
        r.add("spring.liquibase.enabled", () -> "true");
        r.add("spring.kafka.listener.auto-startup", () -> "true");
        r.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
    }
}
