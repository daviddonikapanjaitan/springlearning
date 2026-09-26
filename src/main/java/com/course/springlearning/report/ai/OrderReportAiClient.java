package com.course.springlearning.report.ai;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.order.repository.OrderRepository;
import com.course.springlearning.report.dto.ReportResult;
import com.course.springlearning.report.pdf.OrderPdfReportGenerator;
import com.course.springlearning.report.pdf.OrderPdfReportGenerator.GeneratedPdf;
import com.course.springlearning.user.entity.User;
import com.course.springlearning.user.exception.UserNotFoundException;
import com.course.springlearning.user.repository.UserRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Asks the AI model (OpenRouter) to build the order report of one user by calling {@link OrderReportTools}:
 * the total and a short summary per order status, and a PDF report per order status.
 */
@Component
public class OrderReportAiClient {

    private static final String SYSTEM_PROMPT = """
            You are an order report assistant.
            You never guess or calculate numbers yourself: you always get them by calling the tools.
            For each order status COMPLETED and IN_PROGRESS:
            - call the sumOrderTotalPrice tool to get the total amount,
            - call the generateOrderPdfReport tool to generate the PDF report.
            Then answer with exactly the numbers the sumOrderTotalPrice tool returned.
            For each status also write a summary: one or two short sentences in English describing that total.
            In a summary, write the total amount exactly as the tool returned it, using plain digits only
            (no thousand separators, no decimals, no currency symbol, no words like "million"), for example 3374999.
            """;

    private static final String USER_PROMPT = """
            Create my order report: for my orders with status COMPLETED and for my orders with status IN_PROGRESS,
            give the total amount and a summary, and generate the PDF report.
            """;

    private final ChatClient chatClient;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderPdfReportGenerator pdfGenerator;

    public OrderReportAiClient(ChatClient.Builder chatClientBuilder, OrderRepository orderRepository,
                               UserRepository userRepository, OrderPdfReportGenerator pdfGenerator) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.pdfGenerator = pdfGenerator;
    }

    /**
     * Blocking call, run it in a background thread.
     *
     * @throws IllegalStateException when the AI did not call the tools, answered numbers the tools did not return,
     *                               or wrote a summary without the exact total
     */
    public GeneratedReport createReport(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        OrderReportTools tools = new OrderReportTools(orderRepository, pdfGenerator, userId, user.getFullName());

        ReportResult result = chatClient.prompt()
                .user(USER_PROMPT)
                .tools(tools)
                .call()
                .entity(ReportResult.class);

        verify(result, tools.totals(), tools.pdfs());
        return new GeneratedReport(result, Map.of(
                OrderStatus.COMPLETED, tools.pdfs().get(OrderStatus.COMPLETED).content(),
                OrderStatus.IN_PROGRESS, tools.pdfs().get(OrderStatus.IN_PROGRESS).content()));
    }

    // Never store a number the model made up: totals, summaries and PDFs must match what the tools read from the database
    private static void verify(ReportResult result, Map<OrderStatus, Long> toolTotals, Map<OrderStatus, GeneratedPdf> pdfs) {
        if (result == null) {
            throw new IllegalStateException("AI returned no result");
        }
        Long completed = toolTotals.get(OrderStatus.COMPLETED);
        Long inProgress = toolTotals.get(OrderStatus.IN_PROGRESS);
        if (completed == null || inProgress == null) {
            throw new IllegalStateException("AI did not call sumOrderTotalPrice for both statuses, tool results: " + toolTotals);
        }
        if (!Objects.equals(result.completedTotalAmount(), completed)
                || !Objects.equals(result.inProgressTotalAmount(), inProgress)) {
            throw new IllegalStateException("AI answer " + result + " does not match tool results " + toolTotals);
        }
        verifySummary(OrderStatus.COMPLETED, result.completedSummary(), completed);
        verifySummary(OrderStatus.IN_PROGRESS, result.inProgressSummary(), inProgress);
        verifyPdf(OrderStatus.COMPLETED, pdfs.get(OrderStatus.COMPLETED), completed);
        verifyPdf(OrderStatus.IN_PROGRESS, pdfs.get(OrderStatus.IN_PROGRESS), inProgress);
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

    // The PDF must exist and its "total all orders" must equal the total stored in total_amount
    // (they differ only if orders changed between the two tool calls)
    private static void verifyPdf(OrderStatus status, GeneratedPdf pdf, long total) {
        if (pdf == null) {
            throw new IllegalStateException("AI did not call generateOrderPdfReport for " + status);
        }
        if (pdf.totalAllOrders() != total) {
            throw new IllegalStateException("PDF total " + pdf.totalAllOrders() + " for " + status
                    + " does not match the total " + total);
        }
    }
}
