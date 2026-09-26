package com.course.springlearning.report.pdf;

import com.course.springlearning.order.entity.Order;
import com.course.springlearning.order.entity.OrderStatus;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the PDF report of one user's orders with one order status, using OpenPDF.
 * The PDF lists every order (item price, quantity, total price) and the total of all orders.
 */
@Component
public class OrderPdfReportGenerator {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font TEXT_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
    private static final Font TOTAL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

    private static final Color HEADER_BACKGROUND = new Color(52, 73, 94);
    private static final Color TOTAL_BACKGROUND = new Color(236, 240, 241);

    private static final String[] COLUMNS = {"No", "Invoice Number", "Item Name", "Quantity", "Item Price", "Total Price"};
    // Wide enough to keep an invoice number such as INV-20260926-E3546AD6C4A6 on one line
    private static final float[] COLUMN_WIDTHS = {0.5f, 3.4f, 2.2f, 1.1f, 1.6f, 1.8f};

    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    /**
     * @param orders the orders to list, all with the given status
     * @return the PDF file and the total of all orders printed in it
     */
    public GeneratedPdf generate(Long userId, String userFullName, OrderStatus orderStatus,
                                 List<Order> orders, Instant generatedAt) {
        long totalAllOrders = 0;
        for (Order order : orders) {
            totalAllOrders = Math.addExact(totalAllOrders, order.getTotalPrice());
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, output);
        document.addTitle("Order Report - " + orderStatus);
        document.addCreator("springlearning");
        document.open();
        try {
            document.add(new Paragraph("Order Report - " + orderStatus + " orders", TITLE_FONT));
            document.add(new Paragraph(" ", TEXT_FONT));
            document.add(new Paragraph("User: " + userFullName + " (id " + userId + ")", TEXT_FONT));
            document.add(new Paragraph("Order status: " + orderStatus, TEXT_FONT));
            document.add(new Paragraph("Number of orders: " + orders.size(), TEXT_FONT));
            document.add(new Paragraph("Generated at: " + GENERATED_AT.format(generatedAt), TEXT_FONT));
            document.add(ordersTable(orderStatus, orders, totalAllOrders));
        } finally {
            document.close();
        }
        return new GeneratedPdf(output.toByteArray(), orders.size(), totalAllOrders);
    }

    private static PdfPTable ordersTable(OrderStatus orderStatus, List<Order> orders, long totalAllOrders) {
        PdfPTable table = new PdfPTable(COLUMN_WIDTHS);
        table.setWidthPercentage(100);
        table.setSpacingBefore(12);
        table.setHeaderRows(1); // repeat the header on every page

        for (String column : COLUMNS) {
            PdfPCell cell = cell(column, HEADER_FONT, Element.ALIGN_CENTER);
            cell.setBackgroundColor(HEADER_BACKGROUND);
            table.addCell(cell);
        }

        if (orders.isEmpty()) {
            PdfPCell empty = cell("No orders with status " + orderStatus, TEXT_FONT, Element.ALIGN_CENTER);
            empty.setColspan(COLUMNS.length);
            table.addCell(empty);
        }

        int number = 1;
        for (Order order : orders) {
            table.addCell(cell(String.valueOf(number++), TEXT_FONT, Element.ALIGN_CENTER));
            table.addCell(cell(order.getInvoiceNumber(), TEXT_FONT, Element.ALIGN_LEFT));
            table.addCell(cell(order.getItemName(), TEXT_FONT, Element.ALIGN_LEFT));
            table.addCell(cell(formatNumber(order.getQuantity()), TEXT_FONT, Element.ALIGN_RIGHT));
            table.addCell(cell(formatNumber(order.getItemPrice()), TEXT_FONT, Element.ALIGN_RIGHT));
            table.addCell(cell(formatNumber(order.getTotalPrice()), TEXT_FONT, Element.ALIGN_RIGHT));
        }

        PdfPCell totalLabel = cell("Total all orders", TOTAL_FONT, Element.ALIGN_RIGHT);
        totalLabel.setColspan(COLUMNS.length - 1);
        totalLabel.setBackgroundColor(TOTAL_BACKGROUND);
        table.addCell(totalLabel);
        PdfPCell totalValue = cell(formatNumber(totalAllOrders), TOTAL_FONT, Element.ALIGN_RIGHT);
        totalValue.setBackgroundColor(TOTAL_BACKGROUND);
        table.addCell(totalValue);

        return table;
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        return cell;
    }

    // 15000000 -> 15,000,000 (a new DecimalFormat per call because it is not thread-safe)
    private static String formatNumber(long value) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }

    public record GeneratedPdf(byte[] content, int orderCount, long totalAllOrders) {
    }
}
