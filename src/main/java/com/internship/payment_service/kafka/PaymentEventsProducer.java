package com.internship.payment_service.kafka;

import com.internship.payment_service.config.KafkaTopicsProperties;
import com.internship.payment_service.kafka.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventsProducer {

    private final KafkaTemplate<String, PaymentEvent> paymentEventKafkaTemplate;
    private final KafkaTopicsProperties topics;

    public void send(PaymentEvent event) {

        String key = String.valueOf(event.getOrderId());

        paymentEventKafkaTemplate
                .send(topics.getPaymentsTopic(), key, event)
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to send PaymentEvent {}: {}", event, throwable.getMessage(), throwable);
                    } else if (sendResult != null && sendResult.getRecordMetadata() != null) {
                        log.info("PaymentEvent sent: topic={}, partition={}, offset={}, key={}",
                                sendResult.getRecordMetadata().topic(),
                                sendResult.getRecordMetadata().partition(),
                                sendResult.getRecordMetadata().offset(),
                                key);
                    } else {
                        log.info("PaymentEvent sent (no metadata available), key={}", key);
                    }
                });
    }
}