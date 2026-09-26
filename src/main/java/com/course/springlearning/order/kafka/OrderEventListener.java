package com.course.springlearning.order.kafka;

import com.course.springlearning.order.dto.OrderCreatedMessage;
import com.course.springlearning.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    // Stored in updated_by when the listener receives an order
    private static final String LISTENER_ACTOR = "order-listener";

    private final OrderService orderService;
    private final JsonMapper jsonMapper;

    public OrderEventListener(OrderService orderService, JsonMapper jsonMapper) {
        this.orderService = orderService;
        this.jsonMapper = jsonMapper;
    }

    // Group id comes from spring.kafka.consumer.group-id in application.yaml
    @KafkaListener(topics = OrderKafkaConfig.STREAM_ORDER_USERS_TOPIC)
    public void onOrderCreated(String payload) {
        OrderCreatedMessage message;
        try {
            message = jsonMapper.readValue(payload, OrderCreatedMessage.class);
        } catch (JacksonException e) {
            // Retrying cannot fix a malformed message, so skip it
            log.error("Skipping unreadable message on {}: {}", OrderKafkaConfig.STREAM_ORDER_USERS_TOPIC, payload, e);
            return;
        }
        if (message.orderId() == null) {
            log.error("Skipping message without orderId on {}: {}", OrderKafkaConfig.STREAM_ORDER_USERS_TOPIC, payload);
            return;
        }

        // Database errors are thrown so Spring Kafka retries the message
        orderService.markReceived(message.orderId(), LISTENER_ACTOR);
    }
}
