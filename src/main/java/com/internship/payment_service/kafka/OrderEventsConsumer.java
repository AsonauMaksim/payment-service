package com.internship.payment_service.kafka;

import com.internship.payment_service.config.KafkaTopicsProperties;
import com.internship.payment_service.dto.PaymentRequest;
import com.internship.payment_service.dto.PaymentResponse;
import com.internship.payment_service.kafka.dto.OrderEvent;
import com.internship.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final PaymentService paymentService;
    private final KafkaTopicsProperties topics;

    @KafkaListener(
            topics = "${app.kafka.orders-topic}",
            containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    public void onOrderEvent(OrderEvent orderEvent) {
        log.info("Received OrderEvent: {}", orderEvent);

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderId(orderEvent.getOrderId())
                .userId(orderEvent.getUserId())
                .paymentAmount(orderEvent.getPaymentAmount())
                .eventId(orderEvent.getEventId())
                .build();

        PaymentResponse saved = paymentService.create(paymentRequest);
        log.info("Payment saved from OrderEvent: id={}, status={}", saved.getId(), saved.getStatus());
    }
}
