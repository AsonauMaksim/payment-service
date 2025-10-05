package com.internship.payment_service.service.impl;

import com.internship.payment_service.dto.PaymentRequest;
import com.internship.payment_service.dto.PaymentResponse;
import com.internship.payment_service.dto.PaymentTotalSumResponse;
import com.internship.payment_service.entity.Payment;
import com.internship.payment_service.entity.PaymentStatus;
import com.internship.payment_service.exception.RandomApiUnavailableException;
import com.internship.payment_service.external.RandomNumberClient;
import com.internship.payment_service.kafka.PaymentEventsProducer;
import com.internship.payment_service.kafka.dto.PaymentEvent;
import com.internship.payment_service.mapper.PaymentMapper;
import com.internship.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentMapper mapper;
    @Mock
    private PaymentEventsProducer producer;
    @Mock private
    RandomNumberClient randomNumberClient;

    @InjectMocks
    private PaymentServiceImpl service;

    private PaymentRequest req;

    @BeforeEach
    void setUp() {
        req = PaymentRequest.builder()
                .orderId(100L)
                .userId(200L)
                .paymentAmount(new BigDecimal("49.90"))
                .eventId("evt-123")
                .build();
    }

    @Test
    void create_ShouldReturnExisting_WhenEventIdAlreadyProcessed() {
        Payment existing = Payment.builder()
                .id("p-1")
                .eventId("evt-123")
                .orderId(100L)
                .userId(200L)
                .status(PaymentStatus.SUCCESS)
                .timestamp(Instant.now())
                .paymentAmount(new BigDecimal("49.90"))
                .build();

        when(repository.findByEventId("evt-123")).thenReturn(Optional.of(existing));
        PaymentResponse expected = PaymentResponse.builder()
                .id("p-1").orderId(100L).userId(200L)
                .status(PaymentStatus.SUCCESS)
                .timestamp(existing.getTimestamp())
                .paymentAmount(new BigDecimal("49.90"))
                .eventId("evt-123")
                .build();
        when(mapper.toResponse(existing)).thenReturn(expected);

        PaymentResponse out = service.create(req);

        assertThat(out).isEqualTo(expected);
        verify(repository, never()).save(any());
        verify(producer, never()).send(any());
    }

    @Test
    void create_ShouldSaveAndPublishEvent_WhenNewEvent() {
        when(repository.findByEventId("evt-123")).thenReturn(Optional.empty());

        Payment toSave = Payment.builder()
                .eventId("evt-123")
                .orderId(100L)
                .userId(200L)
                .paymentAmount(new BigDecimal("49.90"))
                .build();
        when(mapper.toEntity(req)).thenReturn(toSave);

        Payment saved = Payment.builder()
                .id("p-2")
                .eventId("evt-123")
                .orderId(100L)
                .userId(200L)
                .status(PaymentStatus.SUCCESS)
                .timestamp(Instant.now())
                .paymentAmount(new BigDecimal("49.90"))
                .build();
        when(repository.save(any(Payment.class))).thenReturn(saved);

        PaymentResponse expected = PaymentResponse.builder()
                .id("p-2").orderId(100L).userId(200L)
                .status(PaymentStatus.SUCCESS)
                .timestamp(saved.getTimestamp())
                .paymentAmount(new BigDecimal("49.90"))
                .eventId("evt-123")
                .build();
        when(mapper.toResponse(saved)).thenReturn(expected);

        PaymentResponse out = service.create(req);

        assertThat(out).isEqualTo(expected);

        ArgumentCaptor<PaymentEvent> evt = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(producer).send(evt.capture());
        assertThat(evt.getValue().getEventId()).isEqualTo("evt-123");
        assertThat(evt.getValue().getOrderId()).isEqualTo(100L);
        assertThat(evt.getValue().getPaymentId()).isEqualTo("p-2");
        assertThat(evt.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void getByOrderId_ShouldMapAll() {
        Payment p1 = Payment.builder()
                .id("p1")
                .orderId(1L)
                .userId(2L)
                .status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("1"))
                .timestamp(Instant.now())
                .build();
        Payment p2 = Payment.builder()
                .id("p2").orderId(1L)
                .userId(3L)
                .status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("2"))
                .timestamp(Instant.now())
                .build();
        when(repository.findByOrderId(1L)).thenReturn(List.of(p1, p2));

        PaymentResponse r1 = PaymentResponse.builder()
                .id("p1")
                .orderId(1L)
                .userId(2L)
                .status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("1"))
                .timestamp(p1.getTimestamp()).build();
        PaymentResponse r2 = PaymentResponse.builder()
                .id("p2")
                .orderId(1L)
                .userId(3L)
                .status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("2"))
                .timestamp(p2.getTimestamp())
                .build();
        when(mapper.toResponse(p1)).thenReturn(r1);
        when(mapper.toResponse(p2)).thenReturn(r2);

        List<PaymentResponse> out = service.getByOrderId(1L);

        assertThat(out).containsExactly(r1, r2);
    }

    @Test
    void getByUserId_ShouldMapAll() {
        Payment p = Payment.builder()
                .id("p1")
                .orderId(5L)
                .userId(7L)
                .status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("3"))
                .timestamp(Instant.now())
                .build();
        when(repository.findByUserId(7L)).thenReturn(List.of(p));

        PaymentResponse r = PaymentResponse.builder()
                .id("p1")
                .orderId(5L)
                .userId(7L)
                .status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("3"))
                .timestamp(p.getTimestamp())
                .build();
        when(mapper.toResponse(p)).thenReturn(r);

        List<PaymentResponse> out = service.getByUserId(7L);

        assertThat(out).containsExactly(r);
    }

    @Test
    void getByStatuses_ShouldMapAll() {
        Payment p = Payment.builder()
                .id("p1")
                .orderId(5L)
                .userId(7L)
                .status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("10"))
                .timestamp(Instant.now())
                .build();
        when(repository.findByStatusIn(Set.of(PaymentStatus.FAILED))).thenReturn(List.of(p));

        PaymentResponse r = PaymentResponse.builder()
                .id("p1")
                .orderId(5L)
                .userId(7L)
                .status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("10"))
                .timestamp(p.getTimestamp())
                .build();
        when(mapper.toResponse(p)).thenReturn(r);

        List<PaymentResponse> out = service.getByStatuses(Set.of(PaymentStatus.FAILED));

        assertThat(out).containsExactly(r);
    }

    @Test
    void getTotalBetween_ShouldReturnZero_WhenNoRows() {
        when(repository.sumSuccessfulBetween(any(), any())).thenReturn(List.of());

        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to   = Instant.parse("2024-12-31T23:59:59Z");

        PaymentTotalSumResponse out = service.getTotalBetween(from, to);

        assertThat(out.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(out.getFrom()).isEqualTo(from);
        assertThat(out.getTo()).isEqualTo(to);
    }

    @Test
    void getTotalBetween_ShouldReturnZero_WhenProjectionTotalIsNull() {
        PaymentRepository.TotalAmountProjection row = () -> null;
        when(repository.sumSuccessfulBetween(any(), any())).thenReturn(List.of(row));

        PaymentTotalSumResponse out = service.getTotalBetween(Instant.now().minusSeconds(10), Instant.now());

        assertThat(out.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getTotalBetween_ShouldReturnValue_WhenProjectionHasTotal() {
        PaymentRepository.TotalAmountProjection row = () -> new BigDecimal("123.45");
        when(repository.sumSuccessfulBetween(any(), any())).thenReturn(List.of(row));

        PaymentTotalSumResponse out = service.getTotalBetween(Instant.now().minusSeconds(10), Instant.now());

        assertThat(out.getTotal()).isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    void create_ShouldSaveAndPublishEvent_WithFailedStatus_WhenOddNumber() {

        PaymentRequest req2 = PaymentRequest.builder()
                .orderId(101L)
                .userId(201L)
                .paymentAmount(new BigDecimal("99.99"))
                .eventId("evt-999")
                .build();

        when(repository.findByEventId("evt-999")).thenReturn(Optional.empty());

        when(randomNumberClient.get()).thenReturn(41);

        Payment toSave = Payment.builder()
                .eventId("evt-999")
                .orderId(101L)
                .userId(201L)
                .paymentAmount(new BigDecimal("99.99"))
                .build();
        when(mapper.toEntity(req2)).thenReturn(toSave);

        Payment saved = Payment.builder()
                .id("p-999")
                .eventId("evt-999")
                .orderId(101L)
                .userId(201L)
                .status(PaymentStatus.FAILED)
                .timestamp(Instant.now())
                .paymentAmount(new BigDecimal("99.99"))
                .build();
        when(repository.save(any(Payment.class))).thenReturn(saved);

        PaymentResponse expected = PaymentResponse.builder()
                .id("p-999")
                .orderId(101L)
                .userId(201L)
                .status(PaymentStatus.FAILED)
                .timestamp(saved.getTimestamp())
                .paymentAmount(new BigDecimal("99.99"))
                .eventId("evt-999")
                .build();
        when(mapper.toResponse(saved)).thenReturn(expected);

        PaymentResponse out = service.create(req2);

        assertThat(out.getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentEvent> evt = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(producer).send(evt.capture());
        assertThat(evt.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(evt.getValue().getOrderId()).isEqualTo(101L);
        assertThat(evt.getValue().getPaymentId()).isEqualTo("p-999");
    }

    @Test
    void create_ShouldSetFailed_WhenRandomApiUnavailable() {

        when(repository.findByEventId("evt-err")).thenReturn(Optional.empty());
        when(randomNumberClient.get()).thenThrow(new RandomApiUnavailableException("down"));

        PaymentRequest reqErr = PaymentRequest.builder()
                .orderId(100L)
                .userId(200L)
                .paymentAmount(new BigDecimal("49.90"))
                .eventId("evt-err")
                .build();

        Payment toSave = Payment.builder()
                .eventId("evt-err")
                .orderId(100L)
                .userId(200L)
                .paymentAmount(new BigDecimal("49.90"))
                .build();
        when(mapper.toEntity(reqErr)).thenReturn(toSave);

        Payment saved = Payment.builder()
                .id("p-err")
                .eventId("evt-err")
                .orderId(100L)
                .userId(200L)
                .status(PaymentStatus.FAILED)   // ожидаем FAILED
                .timestamp(Instant.now())
                .paymentAmount(new BigDecimal("49.90"))
                .build();
        when(repository.save(any(Payment.class))).thenReturn(saved);

        PaymentResponse expected = PaymentResponse.builder()
                .id("p-err")
                .orderId(100L)
                .userId(200L)
                .status(PaymentStatus.FAILED)
                .timestamp(saved.getTimestamp())
                .paymentAmount(new BigDecimal("49.90"))
                .eventId("evt-err")
                .build();
        when(mapper.toResponse(saved)).thenReturn(expected);

        PaymentResponse out = service.create(reqErr);

        assertThat(out.getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentEvent> evt = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(producer).send(evt.capture());
        assertThat(evt.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void create_ShouldForceFailed_WhenSuccessAlreadyExistsForOrder() {

        when(repository.findByEventId("evt-dup")).thenReturn(Optional.empty());
        when(randomNumberClient.get()).thenReturn(42);
        when(repository.existsByOrderIdAndStatus(101L, PaymentStatus.SUCCESS)).thenReturn(true);

        PaymentRequest reqDup = PaymentRequest.builder()
                .orderId(101L)
                .userId(201L)
                .paymentAmount(new BigDecimal("10.00"))
                .eventId("evt-dup")
                .build();

        Payment toSave = Payment.builder()
                .eventId("evt-dup")
                .orderId(101L)
                .userId(201L)
                .paymentAmount(new BigDecimal("10.00"))
                .build();
        when(mapper.toEntity(reqDup)).thenReturn(toSave);

        Payment saved = Payment.builder()
                .id("p-dup")
                .eventId("evt-dup")
                .orderId(101L)
                .userId(201L)
                .status(PaymentStatus.FAILED)
                .timestamp(Instant.now())
                .paymentAmount(new BigDecimal("10.00"))
                .build();
        when(repository.save(any(Payment.class))).thenReturn(saved);

        PaymentResponse expected = PaymentResponse.builder()
                .id("p-dup")
                .orderId(101L)
                .userId(201L)
                .status(PaymentStatus.FAILED)
                .timestamp(saved.getTimestamp())
                .paymentAmount(new BigDecimal("10.00"))
                .eventId("evt-dup")
                .build();
        when(mapper.toResponse(saved)).thenReturn(expected);

        PaymentResponse out = service.create(reqDup);

        assertThat(out.getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentEvent> evt = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(producer).send(evt.capture());
        assertThat(evt.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}