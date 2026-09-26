package com.course.springlearning.report.ai;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tools the AI can call while building one report.
 * A new instance is created per report and is bound to one user, so the AI can only read that user's orders.
 * Every result is recorded so the AI's final answer can be checked against the real database values.
 */
public class OrderReportTools {

    private static final Logger log = LoggerFactory.getLogger(OrderReportTools.class);

    private final OrderRepository orderRepository;
    private final Long userId;
    private final Map<OrderStatus, Long> results = new ConcurrentHashMap<>();

    public OrderReportTools(OrderRepository orderRepository, Long userId) {
        this.orderRepository = orderRepository;
        this.userId = userId;
    }

    @Tool(description = "Calculates the total amount of the user's orders with the given order status: "
            + "the sum of total_price of those orders. Returns 0 when the user has no such orders.")
    public long sumOrderTotalPrice(
            @ToolParam(description = "Order status to sum, either COMPLETED or IN_PROGRESS") String orderStatus) {
        OrderStatus status = parseStatus(orderStatus);
        long total = orderRepository.sumTotalPriceByUserIdAndOrderStatus(userId, status.name());
        results.put(status, total);
        log.info("Tool sumOrderTotalPrice(userId={}, orderStatus={}) = {}", userId, status, total);
        return total;
    }

    /** What each tool call returned, by order status. */
    public Map<OrderStatus, Long> results() {
        return Map.copyOf(results);
    }

    // The error message is sent back to the AI, so it can retry with a valid status
    private static OrderStatus parseStatus(String orderStatus) {
        String normalized = orderStatus == null ? "" : orderStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "COMPLETED" -> OrderStatus.COMPLETED;
            case "IN_PROGRESS" -> OrderStatus.IN_PROGRESS;
            default -> throw new IllegalArgumentException(
                    "Invalid orderStatus '" + orderStatus + "', use COMPLETED or IN_PROGRESS");
        };
    }
}
