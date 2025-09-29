package com.internship.payment_service.integration;

import com.internship.payment_service.PaymentServiceApplication;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(
        classes = PaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.5.3");
    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:7.0");

    @Container
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(KAFKA_IMAGE)
                    .waitingFor(Wait.forListeningPort());

    @Container
    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(MONGO_IMAGE)
                    .waitingFor(Wait.forListeningPort());

    static {
        KAFKA.start();
        MONGO.start();
    }

    protected static final String ORDERS_TOPIC = "orders";
    protected static final String PAYMENTS_TOPIC = "payments";
    protected static final String CONSUMER_GROUP = "payment-service-it";


    @DynamicPropertySource
    static void registerDynamicProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.kafka.producer.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.kafka.consumer.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.kafka.listener.auto-startup", () -> "true");
        r.add("spring.data.mongodb.uri", () -> MONGO.getConnectionString() + "/payments_db");

        r.add("app.kafka.orders-topic", () -> ORDERS_TOPIC);
        r.add("app.kafka.payments-topic", () -> PAYMENTS_TOPIC);
        r.add("app.kafka.consumer-group", () -> CONSUMER_GROUP);

        r.add("spring.kafka.listener.missing-topics-fatal", () -> "false");

        r.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeAll
    static void awaitKafkaReadyHard() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        long deadline = System.currentTimeMillis() + 60_000;
        Exception last = null;
        try (org.apache.kafka.clients.admin.AdminClient admin =
                     org.apache.kafka.clients.admin.AdminClient.create(props)) {
            while (System.currentTimeMillis() < deadline) {
                try {
                    admin.listTopics().names().get(2, java.util.concurrent.TimeUnit.SECONDS);
                    return; // брокер отвечает — ок
                } catch (Exception e) {
                    last = e;
                    Thread.sleep(200);
                }
            }
        }
        throw new IllegalStateException("Kafka is not reachable: " + KAFKA.getBootstrapServers(),
                last);
    }
}
