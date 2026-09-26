package com.course.springlearning.report.ai;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.order.repository.OrderRepository;
import com.course.springlearning.report.dto.ReportResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Asks the AI model (OpenRouter) to calculate the order totals of one user by calling {@link OrderReportTools}
 * and to write a short summary per order status.
 */
@Component
public class OrderReportAiClient {

    private static final String SYSTEM_PROMPT = """
            You are an order report assistant.
            You never guess or calculate numbers yourself: you always get them by calling the sumOrderTotalPrice tool.
            Call the tool once with orderStatus COMPLETED and once with orderStatus IN_PROGRESS,
            then answer with exactly the numbers the tool returned.
            For each status also write a summary: one or two short sentences in English describing that total.
            In a summary, write the total amount exactly as the tool returned it, using plain digits only
            (no thousand separators, no decimals, no currency symbol, no words like "million"), for example 3374999.
            """;

    private static final String USER_PROMPT = """
            Create my order report: the total amount and a summary of my orders with status COMPLETED,
            and the total amount and a summary of my orders with status IN_PROGRESS.
            """;

    private final ChatClient chatClient;
    private final OrderRepository orderRepository;

    public OrderReportAiClient(ChatClient.Builder chatClientBuilder, OrderRepository orderRepository) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.orderRepository = orderRepository;
    }

    /**
     * Blocking call, run it in a background thread.
     *
     * @throws IllegalStateException when the AI did not call the tools, answered numbers the tools did not return,
     *                               or wrote a summary without the exact total
     */
    public ReportResult createReport(Long userId) {
        OrderReportTools tools = new OrderReportTools(orderRepository, userId);

        ReportResult result = chatClient.prompt()
                .user(USER_PROMPT)
                .tools(tools)
                .call()
                .entity(ReportResult.class);

        verify(result, tools.results());
        return result;
    }

    // Never store a number the model made up: totals and summaries must match what the tool read from the database
    private static void verify(ReportResult result, Map<OrderStatus, Long> toolResults) {
        if (result == null) {
            throw new IllegalStateException("AI returned no result");
        }
        Long completed = toolResults.get(OrderStatus.COMPLETED);
        Long inProgress = toolResults.get(OrderStatus.IN_PROGRESS);
        if (completed == null || inProgress == null) {
            throw new IllegalStateException("AI did not call the tool for both statuses, tool results: " + toolResults);
        }
        if (!Objects.equals(result.completedTotalAmount(), completed)
                || !Objects.equals(result.inProgressTotalAmount(), inProgress)) {
            throw new IllegalStateException("AI answer " + result + " does not match tool results " + toolResults);
        }
        verifySummary(OrderStatus.COMPLETED, result.completedSummary(), completed);
        verifySummary(OrderStatus.IN_PROGRESS, result.inProgressSummary(), inProgress);
    }

    // The summary must mention the exact total as a whole number (so "15000000" matches, "150000001" does not)
    private static void verifySummary(OrderStatus status, String summary, long total) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalStateException("AI returned an empty summary for " + status);
        }
        Pattern exactTotal = Pattern.compile("(?<![\\d.,])" + total + "(?![\\d]|[.,]\\d)");
        if (!exactTotal.matcher(summary).find()) {
            throw new IllegalStateException("AI summary for " + status + " does not state the total " + total
                    + ": " + summary);
        }
    }
}
