package com.course.springlearning.order.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class OrderKafkaConfig {

    public static final String STREAM_ORDER_USERS_TOPIC = "stream-order-users";

    // Created on startup by Spring's KafkaAdmin if it does not exist yet.
    // 1 replica because docker-compose runs a single broker.
    @Bean
    public NewTopic streamOrderUsersTopic() {
        return TopicBuilder.name(STREAM_ORDER_USERS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
