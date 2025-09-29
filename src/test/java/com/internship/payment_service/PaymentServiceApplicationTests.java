package com.internship.payment_service;

import com.internship.payment_service.external.RandomNumberClient;
import com.internship.payment_service.kafka.PaymentEventsProducer;
import com.internship.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

	@MockBean
	private PaymentRepository paymentRepository;
	@MockBean private PaymentEventsProducer paymentEventsProducer;
	@MockBean private RandomNumberClient randomNumberClient;

	@Test
	void contextLoads() {}
}
