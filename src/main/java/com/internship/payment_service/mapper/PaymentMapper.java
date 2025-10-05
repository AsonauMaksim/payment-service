package com.internship.payment_service.mapper;

import com.internship.payment_service.dto.PaymentRequest;
import com.internship.payment_service.dto.PaymentResponse;
import com.internship.payment_service.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    Payment toEntity(PaymentRequest request);

    PaymentResponse toResponse(Payment payment);
}
