package com.internship.payment_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@RequiredArgsConstructor
public class KafkaTopicsConfig {

    private final KafkaTopicsProperties topics;

    @Bean
    public KafkaAdmin.NewTopics appTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(topics.getOrdersTopic()).partitions(1).replicas(1).build(),
                TopicBuilder.name(topics.getPaymentsTopic()).partitions(1).replicas(1).build()
        );
    }
}
