package com.internship.payment_service.integration;

import com.internship.payment_service.PaymentServiceApplication;
import com.internship.payment_service.entity.Payment;
import com.internship.payment_service.entity.PaymentStatus;
import com.internship.payment_service.external.RandomNumberClient;
import com.internship.payment_service.kafka.dto.OrderEvent;
import com.internship.payment_service.kafka.dto.PaymentEvent;
import com.internship.payment_service.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PaymentFlowIT extends BaseIntegrationTest {

    @MockBean
    private RandomNumberClient randomNumberClient;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeAll
    static void createTopics() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            List<NewTopic> topics = List.of(
                    new NewTopic(ORDERS_TOPIC, 1, (short) 1),
                    new NewTopic(PAYMENTS_TOPIC, 1, (short) 1)
            );
            try {
                admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
            admin.describeTopics(List.of(ORDERS_TOPIC, PAYMENTS_TOPIC))
                    .all().get(30, TimeUnit.SECONDS);
        }
    }

    private KafkaTemplate<String, OrderEvent> orderEventTemplate() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg));
    }

    private KafkaConsumer<String, PaymentEvent> paymentEventConsumer(String group) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PaymentEvent> jd = new JsonDeserializer<>(PaymentEvent.class, false);
        jd.addTrustedPackages("com.internship.payment_service.kafka.dto");

        return new KafkaConsumer<>(cfg, new StringDeserializer(), jd);
    }

    private void awaitMongoReady() throws Exception {
        String conn = MONGO.getConnectionString(); // из BaseIntegrationTest
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        Exception last = null;

        while (System.currentTimeMillis() < deadline) {
            try (MongoClient client = MongoClients.create(conn)) {
                client.getDatabase("admin").runCommand(new Document("ping", 1));
                client.getDatabase("payments_db").listCollectionNames().first();
                return;
            } catch (Exception e) {
                last = e;
                Thread.sleep(250);
            }
        }
        throw new AssertionError("MongoDB was not ready in time: " + (last != null ? last.getMessage() : ""));
    }

    @Test
    void e2e_ShouldCreatePayment_AndPublishPaymentEvent() throws Exception {
        Mockito.when(randomNumberClient.get()).thenReturn(42);

        awaitMongoReady();

        String eventId = UUID.randomUUID().toString();
        Long orderId = 111L;
        Long userId = 222L;
        BigDecimal amount = new BigDecimal("15.50");

        try (KafkaConsumer<String, PaymentEvent> consumer =
                     paymentEventConsumer("it-consumer-" + UUID.randomUUID())) {

            consumer.subscribe(List.of(PAYMENTS_TOPIC));
            long t0 = System.currentTimeMillis();
            while (consumer.assignment().isEmpty() && System.currentTimeMillis() - t0 < 5_000) {
                consumer.poll(Duration.ofMillis(100));
            }

            assertThat(consumer.assignment())
                    .as("Partitions must be assigned")
                    .isNotEmpty();

            OrderEvent orderEvent = OrderEvent.builder()
                    .eventId(eventId)
                    .orderId(orderId)
                    .userId(userId)
                    .paymentAmount(amount)
                    .build();

            KafkaTemplate<String, OrderEvent> tpl = orderEventTemplate();
            tpl.send(ORDERS_TOPIC, String.valueOf(orderId), orderEvent).get(10, TimeUnit.SECONDS);
            tpl.flush();

            PaymentEvent received = null;
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(45).toMillis();
            while (System.currentTimeMillis() < deadline && received == null) {
                ConsumerRecords<String, PaymentEvent> records = consumer.poll(Duration.ofMillis(500));
                for (var rec : records) {
                    received = rec.value();
                    break;
                }
            }

            assertThat(received).as("PaymentEvent must be received").isNotNull();
            assertThat(received.getOrderId()).isEqualTo(orderId);
            assertThat(received.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(received.getEventId()).isEqualTo(eventId);
        }

        List<Payment> byOrder = paymentRepository.findByOrderId(orderId);
        assertThat(byOrder).hasSize(1);
        assertThat(byOrder.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(byOrder.get(0).getEventId()).isEqualTo(eventId);
        assertThat(byOrder.get(0).getPaymentAmount()).isEqualByComparingTo(amount);
        assertThat(byOrder.get(0).getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }
}
