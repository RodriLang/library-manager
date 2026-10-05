package com.rodrilang.librarymanager.purchasing.order.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrder;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderItemRepository;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public PurchaseOrderExportFile export(Long orderId, PurchaseOrderExportFormat format) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = orderRepository.findByIdAndBookstoreId(orderId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el pedido con ID: " + orderId
                ));

        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderIdOrderByIdAsc(orderId);
        String baseName = "pedido-" + sanitizeFileName(order.getOrderNumber());

        return switch (format) {
            case PDF -> new PurchaseOrderExportFile(
                    baseName + ".pdf",
                    PDF_CONTENT_TYPE,
                    renderPdf(order, items)
            );
            case XLSX -> new PurchaseOrderExportFile(
                    baseName + ".xlsx",
                    XLSX_CONTENT_TYPE,
                    renderXlsx(order, items)
            );
            case CSV -> new PurchaseOrderExportFile(
                    baseName + ".csv",
                    CSV_CONTENT_TYPE,
                    renderCsv(order, items)
            );
        };
    }

    private byte[] renderXlsx(PurchaseOrder order, List<PurchaseOrderItem> items) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Pedido");

            var boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);

            int rowIndex = 0;
            rowIndex = addMetadataRow(sheet, rowIndex, "Pedido", order.getOrderNumber(), boldStyle);
            rowIndex = addMetadataRow(sheet, rowIndex, "Librería", order.getBookstore().getName(), boldStyle);
            rowIndex = addMetadataRow(sheet, rowIndex, "Proveedor", order.getProvider().getName(), boldStyle);
            rowIndex = addMetadataRow(sheet, rowIndex, "Estado", statusLabel(order.getStatus()), boldStyle);
            rowIndex = addMetadataRow(
                    sheet,
                    rowIndex,
                    "Fecha",
                    DATE_TIME.format(order.getCreatedAt().atZone(BUSINESS_ZONE)),
                    boldStyle
            );

            if (order.getNotes() != null && !order.getNotes().isBlank()) {
                rowIndex = addMetadataRow(sheet, rowIndex, "Observaciones del pedido", order.getNotes(), boldStyle);
            }

            rowIndex++;
            Row header = sheet.createRow(rowIndex++);
            String[] headers = {"ISBN", "Título", "Cantidad", "Observaciones"};
            for (int column = 0; column < headers.length; column++) {
                var cell = header.createCell(column);
                cell.setCellValue(headers[column]);
                cell.setCellStyle(boldStyle);
            }

            for (PurchaseOrderItem item : items) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nullSafe(item.getBook().getPreferredIsbn()));
                row.createCell(1).setCellValue(nullSafe(item.getBook().getTitle()));
                row.createCell(2).setCellValue(item.getQuantity());
                row.createCell(3).setCellValue(nullSafe(item.getNotes()));
            }

            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 55 * 256);
            sheet.setColumnWidth(2, 12 * 256);
            sheet.setColumnWidth(3, 42 * 256);

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("No se pudo generar el archivo Excel del pedido.");
        }
    }

    private int addMetadataRow(
            Sheet sheet,
            int rowIndex,
            String label,
            String value,
            CellStyle boldStyle
    ) {
        Row row = sheet.createRow(rowIndex);
        var labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(boldStyle);
        row.createCell(1).setCellValue(nullSafe(value));
        return rowIndex + 1;
    }

    private byte[] renderCsv(PurchaseOrder order, List<PurchaseOrderItem> items) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Pedido,").append(csvValue(order.getOrderNumber())).append('\n');
        csv.append("Librería,").append(csvValue(order.getBookstore().getName())).append('\n');
        csv.append("Proveedor,").append(csvValue(order.getProvider().getName())).append('\n');
        csv.append("Estado,").append(csvValue(statusLabel(order.getStatus()))).append('\n');
        csv.append("Fecha,").append(csvValue(
                DATE_TIME.format(order.getCreatedAt().atZone(BUSINESS_ZONE))
        )).append('\n');

        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            csv.append("Observaciones del pedido,").append(csvValue(order.getNotes())).append('\n');
        }

        csv.append('\n');
        csv.append("ISBN,Título,Cantidad,Observaciones\n");

        for (PurchaseOrderItem item : items) {
            csv.append(csvValue(item.getBook().getPreferredIsbn())).append(',')
                    .append(csvValue(item.getBook().getTitle())).append(',')
                    .append(item.getQuantity()).append(',')
                    .append(csvValue(item.getNotes())).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderPdf(PurchaseOrder order, List<PurchaseOrderItem> items) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, output);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

            String title = order.getStatus() == PurchaseOrderStatus.DRAFT
                    ? "PEDIDO - BORRADOR"
                    : "PEDIDO";

            Paragraph heading = new Paragraph(title, titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingAfter(8);
            document.add(heading);

            Paragraph number = new Paragraph(order.getOrderNumber(), subtitleFont);
            number.setAlignment(Element.ALIGN_CENTER);
            number.setSpacingAfter(16);
            document.add(number);

            document.add(new Paragraph("Librería: " + order.getBookstore().getName(), normalFont));
            document.add(new Paragraph("Proveedor: " + order.getProvider().getName(), normalFont));
            document.add(new Paragraph("Estado: " + statusLabel(order.getStatus()), normalFont));
            document.add(new Paragraph(
                    "Fecha: " + DATE_TIME.format(order.getCreatedAt().atZone(BUSINESS_ZONE)),
                    normalFont
            ));

            if (order.getNotes() != null && !order.getNotes().isBlank()) {
                Paragraph notes = new Paragraph("Observaciones del pedido: " + order.getNotes(), normalFont);
                notes.setSpacingAfter(12);
                document.add(notes);
            } else {
                Paragraph gap = new Paragraph(" ", normalFont);
                gap.setSpacingAfter(6);
                document.add(gap);
            }

            PdfPTable table = new PdfPTable(new float[]{20, 46, 12, 22});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            addPdfHeader(table, "ISBN", headerFont);
            addPdfHeader(table, "Título", headerFont);
            addPdfHeader(table, "Cantidad", headerFont);
            addPdfHeader(table, "Observaciones", headerFont);

            for (PurchaseOrderItem item : items) {
                addPdfCell(table, nullSafe(item.getBook().getPreferredIsbn()), normalFont, Element.ALIGN_LEFT);
                addPdfCell(table, nullSafe(item.getBook().getTitle()), normalFont, Element.ALIGN_LEFT);
                addPdfCell(table, String.valueOf(item.getQuantity()), normalFont, Element.ALIGN_CENTER);
                addPdfCell(table, nullSafe(item.getNotes()), normalFont, Element.ALIGN_LEFT);
            }

            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("No se pudo generar el PDF del pedido.");
        }
    }

    private void addPdfHeader(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addPdfCell(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private String statusLabel(PurchaseOrderStatus status) {
        return switch (status) {
            case DRAFT -> "Borrador";
            case SENT -> "Enviado";
            case CANCELLED -> "Cancelado";
        };
    }

    private String csvValue(String value) {
        if (value == null) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
