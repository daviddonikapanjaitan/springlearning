package com.course.springlearning.order.kafka;

import com.course.springlearning.order.dto.OrderCreatedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Sends the message only after the transaction commits, so the listener never receives
     * an order that is not in the database yet or was rolled back.
     * The order id is the message key, so all messages of one order land on the same partition.
     */
    public void publishOrderCreatedAfterCommit(OrderCreatedMessage message) {
        String key = String.valueOf(message.orderId());
        String payload = jsonMapper.writeValueAsString(message);

        Runnable send = () -> {
            // The order is already committed at this point, so a Kafka failure is logged instead of failing the request
            try {
                kafkaTemplate.send(OrderKafkaConfig.STREAM_ORDER_USERS_TOPIC, key, payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish order {} to {}", key, OrderKafkaConfig.STREAM_ORDER_USERS_TOPIC, ex);
                            }
                        });
            } catch (RuntimeException e) {
                log.error("Failed to publish order {} to {}", key, OrderKafkaConfig.STREAM_ORDER_USERS_TOPIC, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
