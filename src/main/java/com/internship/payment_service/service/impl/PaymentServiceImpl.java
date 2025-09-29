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
import com.internship.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final PaymentEventsProducer paymentEventsProducer;
    private final RandomNumberClient randomNumberClient;

    @Override
    public PaymentResponse create(PaymentRequest request) {
        if (StringUtils.hasText(request.getEventId())) {

            var existing = repository.findByEventId(request.getEventId());
            if (existing.isPresent()) {
                return mapper.toResponse(existing.get());
            }
        }

        Payment entity = mapper.toEntity(request);

        PaymentStatus status;
        try {
            int rnd = randomNumberClient.get();
            status = (rnd % 2 == 0) ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        } catch (RandomApiUnavailableException ex) {
            status = PaymentStatus.FAILED;
        }

        if (status == PaymentStatus.SUCCESS &&
                repository.existsByOrderIdAndStatus(entity.getOrderId(), PaymentStatus.SUCCESS)) {
            status = PaymentStatus.FAILED;
        }
        entity.setStatus(status);

        entity.setTimestamp(Instant.now());

        entity = repository.save(entity);

        PaymentEvent event = PaymentEvent.builder()
                .eventId(entity.getEventId())
                .orderId(entity.getOrderId())
                .paymentId(entity.getId())
                .status(entity.getStatus())
                .build();
        paymentEventsProducer.send(event);

        return mapper.toResponse(entity);
    }

    @Override
    public List<PaymentResponse> getByOrderId(Long orderId) {
        return repository.findByOrderId(orderId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<PaymentResponse> getByUserId(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<PaymentResponse> getByStatuses(Set<PaymentStatus> statuses) {
        return repository.findByStatusIn(statuses).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public PaymentTotalSumResponse getTotalBetween(Instant from, Instant to) {
        List<PaymentRepository.TotalAmountProjection> rows = repository.sumSuccessfulBetween(from, to);
        BigDecimal total = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).getTotal();
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        return PaymentTotalSumResponse.builder()
                .total(total)
                .from(from)
                .to(to)
                .build();
    }
}
