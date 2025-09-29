package com.internship.payment_service.integration;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentFlowIT extends BaseIntegrationTest {

    @MockBean
    private RandomNumberClient randomNumberClient;

    @Autowired
    private PaymentRepository paymentRepository;

    private KafkaTemplate<String, OrderEvent> orderEventTemplate() {
        HashMap<String, Object> cfg = new HashMap<>();
        cfg.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cfg.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg));
    }

    private KafkaConsumer<String, PaymentEvent> paymentEventConsumer(String group) {
        HashMap<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PaymentEvent> jd = new JsonDeserializer<>(PaymentEvent.class);
        jd.addTrustedPackages("com.internship.payment_service.kafka.dto");
        return new KafkaConsumer<>(cfg, new StringDeserializer(), jd);
    }

    private void awaitMongoReady() throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                paymentRepository.count();
                return;
            } catch (Exception ignored) {
                Thread.sleep(200);
            }
        }
        throw new AssertionError("MongoDB was not ready in time");
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

            consumer.subscribe(Collections.singletonList(PAYMENTS_TOPIC));
            consumer.poll(Duration.ofMillis(0));

            OrderEvent orderEvent = OrderEvent.builder()
                    .eventId(eventId)
                    .orderId(orderId)
                    .userId(userId)
                    .paymentAmount(amount)
                    .build();

            KafkaTemplate<String, OrderEvent> tpl = orderEventTemplate();
            tpl.send(ORDERS_TOPIC, String.valueOf(orderId), orderEvent)
                    .get(10, TimeUnit.SECONDS);
            tpl.flush();

            PaymentEvent received = null;
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(25).toMillis();
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
