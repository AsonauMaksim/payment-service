package com.internship.payment_service.integration;

import com.internship.payment_service.PaymentServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

@SpringBootTest(
        classes = PaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@ContextConfiguration(initializers = TestContainersInitializer.class)
public abstract class BaseIntegrationTest {

//    private static final DockerImageName KAFKA_IMAGE =
//            DockerImageName.parse("apache/kafka:3.7.1"); // можно 3.7.0
//    private static final DockerImageName MONGO_IMAGE =
//            DockerImageName.parse("mongo:7.0");
//
//    @Container
//    protected static final KafkaContainer KAFKA =
//            new KafkaContainer(KAFKA_IMAGE).waitingFor(Wait.forListeningPort());
//
//    @Container
//    protected static final MongoDBContainer MONGO =
//            new MongoDBContainer(MONGO_IMAGE).waitingFor(Wait.forListeningPort());
//
//    protected static final String ORDERS_TOPIC = "orders";
//    protected static final String PAYMENTS_TOPIC = "payments";
//    protected static final String CONSUMER_GROUP = "payment-service-it";
//
//    // ВАЖНО: подставляем ТОЛЬКО эти два spring.* через контейнеры.
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
//        r.add("spring.data.mongodb.uri", () -> MONGO.getConnectionString() + "/payments_db");
//
//        // свои app.* и флаги
//        r.add("app.kafka.orders-topic", () -> ORDERS_TOPIC);
//        r.add("app.kafka.payments-topic", () -> PAYMENTS_TOPIC);
//        r.add("app.kafka.consumer-group", () -> CONSUMER_GROUP);
//
//        r.add("spring.kafka.listener.auto-startup", () -> "true");
//        r.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
//        r.add("spring.liquibase.enabled", () -> "true");
//    }
}
