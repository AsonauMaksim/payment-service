package com.internship.payment_service.integration;

import com.internship.payment_service.PaymentServiceApplication;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        classes = PaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.5.3");
    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:7.0");

    @Container
    protected static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    @Container
    protected static final MongoDBContainer MONGO = new MongoDBContainer(MONGO_IMAGE);

    protected static final String ORDERS_TOPIC = "orders";
    protected static final String PAYMENTS_TOPIC = "payments";
    protected static final String CONSUMER_GROUP = "payment-service-it";

    @DynamicPropertySource
    static void registerDynamicProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.data.mongodb.uri", () -> MONGO.getConnectionString() + "/payments_db");

        r.add("app.kafka.orders-topic", () -> ORDERS_TOPIC);
        r.add("app.kafka.payments-topic", () -> PAYMENTS_TOPIC);
        r.add("app.kafka.consumer-group", () -> CONSUMER_GROUP);

        r.add("spring.kafka.admin.properties.bootstrap.servers", KAFKA::getBootstrapServers);
        r.add("spring.kafka.admin.auto-create", () -> "true");
        r.add("spring.kafka.listener.missing-topics-fatal", () -> "false");

        r.add("spring.liquibase.enabled", () -> "true");
    }
}
