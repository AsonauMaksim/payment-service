package com.internship.payment_service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.payment_service.entity.Payment;
import com.internship.payment_service.entity.PaymentStatus;
import com.internship.payment_service.exception.RandomApiUnavailableException;
import com.internship.payment_service.external.RandomNumberClient;
import com.internship.payment_service.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository repo;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RandomNumberClient randomNumberClient;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void create_ShouldReturnSuccess_AndBeIdempotentByEventId() throws Exception {
        Mockito.when(randomNumberClient.get()).thenReturn(42);

        String payload = """
                {
                  "orderId": 123,
                  "userId": 456,
                  "paymentAmount": 99.99,
                  "eventId": "evt-abc"
                }
                """;

        String resp1 = mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload.getBytes(StandardCharsets.UTF_8))
                )
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.orderId").value(123))
                .andExpect(jsonPath("$.userId").value(456))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode r1 = objectMapper.readTree(resp1);
        String id1 = r1.get("id").asText();

        String resp2 = mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload.getBytes(StandardCharsets.UTF_8))
                )
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.orderId").value(123))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode r2 = objectMapper.readTree(resp2);
        String id2 = r2.get("id").asText();

        assertThat(id2).isEqualTo(id1);

        List<Payment> all = repo.findByOrderId(123L);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo(id1);
        assertThat(all.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void getByOrderUserStatuses_ShouldReturnExpectedData() throws Exception {
        Instant now = Instant.now();

        repo.save(Payment.builder()
                .orderId(10L).userId(1L).status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("10.00")).timestamp(now.minusSeconds(50)).build());

        repo.save(Payment.builder()
                .orderId(10L).userId(2L).status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("20.00")).timestamp(now.minusSeconds(40)).build());

        repo.save(Payment.builder()
                .orderId(11L).userId(1L).status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("30.00")).timestamp(now.minusSeconds(30)).build());

        mockMvc.perform(get("/api/v1/payments/order/{orderId}", 10L))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/payments/user/{userId}", 1L))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/payments/statuses")
                        .param("statuses", "FAILED"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getTotalSum_ShouldReturnOnlySuccessWithinRange() throws Exception {
        Instant now = Instant.now();

        repo.save(Payment.builder()
                .orderId(100L).userId(1L).status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("10.00")).timestamp(now.minusSeconds(100)).build());

        repo.save(Payment.builder()
                .orderId(100L).userId(1L).status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("20.00")).timestamp(now.minusSeconds(90)).build());

        repo.save(Payment.builder()
                .orderId(101L).userId(1L).status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("30.00")).timestamp(now.minusSeconds(10_000)).build());

        String from = now.minusSeconds(200).toString();
        String to   = now.toString();

        mockMvc.perform(get("/api/v1/payments/total_sum")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.total").value(10.00));
    }

    @Test
    void create_ShouldReturnFailed_WhenOddNumber() throws Exception {
        Mockito.when(randomNumberClient.get()).thenReturn(41); // нечётное -> FAILED

        String payload = """
            {
              "orderId": 124,
              "userId": 456,
              "paymentAmount": 50.00,
              "eventId": "evt-failed-odd"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload.getBytes(StandardCharsets.UTF_8))
                )
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.orderId").value(124))
                .andExpect(jsonPath("$.status").value("FAILED"));

        var list = repo.findByOrderId(124L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void create_ShouldReturnFailed_WhenRandomApiUnavailable() throws Exception {
        Mockito.when(randomNumberClient.get()).thenThrow(new RandomApiUnavailableException("down"));

        String payload = """
            {
              "orderId": 125,
              "userId": 456,
              "paymentAmount": 77.77,
              "eventId": "evt-failed-api"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload.getBytes(StandardCharsets.UTF_8))
                )
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.orderId").value(125))
                .andExpect(jsonPath("$.status").value("FAILED"));

        var list = repo.findByOrderId(125L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void create_ShouldReturn400_WhenRequestInvalid() throws Exception {
        String badPayload = """
            {
              "orderId": 200,
              "paymentAmount": 10.00,
              "eventId": "evt-bad"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badPayload.getBytes(StandardCharsets.UTF_8))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatuses_ShouldAcceptMultipleStatuses() throws Exception {
        Instant now = Instant.now();

        repo.save(Payment.builder()
                .orderId(300L).userId(9L).status(PaymentStatus.SUCCESS)
                .paymentAmount(new BigDecimal("1.00")).timestamp(now.minusSeconds(10)).build());

        repo.save(Payment.builder()
                .orderId(301L).userId(9L).status(PaymentStatus.FAILED)
                .paymentAmount(new BigDecimal("2.00")).timestamp(now.minusSeconds(9)).build());

        mockMvc.perform(get("/api/v1/payments/statuses")
                        .param("statuses", "SUCCESS", "FAILED"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getStatuses_ShouldReturn400_WhenInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/payments/statuses")
                        .param("statuses", "LOL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByOrder_ShouldReturnEmptyArray_WhenNoPayments() throws Exception {
        mockMvc.perform(get("/api/v1/payments/order/{orderId}", 99999L))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getByUser_ShouldReturnEmptyArray_WhenNoPayments() throws Exception {
        mockMvc.perform(get("/api/v1/payments/user/{userId}", 88888L))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getTotalSum_ShouldReturnZero_WhenNoRows() throws Exception {
        Instant now = Instant.now();
        mockMvc.perform(get("/api/v1/payments/total_sum")
                        .param("from", now.minusSeconds(1000).toString())
                        .param("to", now.toString()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getTotalSum_ShouldReturn400_WhenMissingParam() throws Exception {
        Instant now = Instant.now();
        mockMvc.perform(get("/api/v1/payments/total_sum")
                        .param("from", now.minusSeconds(1000).toString()))
                .andExpect(status().isBadRequest());
    }
}
