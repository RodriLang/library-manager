package com.rodrilang.librarymanager.fiscal.service.impl;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.model.GrossIncomeRegime;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;
import com.rodrilang.librarymanager.fiscal.repository.BookstoreFiscalSettingsRepository;
import com.rodrilang.librarymanager.fiscal.repository.FiscalDocumentRepository;
import com.rodrilang.librarymanager.fiscal.service.FiscalPdfService;
import com.rodrilang.librarymanager.sales.model.SaleItem;
import com.rodrilang.librarymanager.sales.model.SalePayment;
import com.rodrilang.librarymanager.sales.repository.SaleItemRepository;
import com.rodrilang.librarymanager.sales.repository.SalePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FiscalPdfServiceImpl implements FiscalPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private final FiscalDocumentRepository documentRepository;
    private final BookstoreFiscalSettingsRepository settingsRepository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final BookstoreContext bookstoreContext;

    @Override
    @Transactional(readOnly = true)
    public byte[] generate(Long documentId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        FiscalDocument fiscal = documentRepository.findByIdAndBookstoreId(documentId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el comprobante fiscal con ID: " + documentId
                ));

        if (fiscal.getStatus() != FiscalDocumentStatus.AUTHORIZED) {
            throw new BusinessException("Sólo se puede imprimir un comprobante autorizado por ARCA.");
        }

        BookstoreFiscalSettings settings = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseThrow(() -> new BusinessException("No existe configuración fiscal."));
        List<SaleItem> items = itemRepository.findAllBySaleIdOrderByIdAsc(fiscal.getSale().getId());
        List<SalePayment> payments = paymentRepository.findAllBySaleIdOrderByIdAsc(fiscal.getSale().getId());

        return render(fiscal, settings, items, payments);
    }

    private byte[] render(
            FiscalDocument fiscal,
            BookstoreFiscalSettings settings,
            List<SaleItem> items,
            List<SalePayment> payments
    ) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, output);
            document.open();

            addHeader(document, fiscal, settings);
            addRecipient(document, fiscal);
            addItems(document, items);
            addTotals(document, fiscal);
            addPayments(document, payments);
            addFiscalFooter(document, fiscal);

            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("No se pudo generar el PDF del comprobante.");
        }
    }

    private void addHeader(
            Document document,
            FiscalDocument fiscal,
            BookstoreFiscalSettings settings
    ) throws Exception {
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font voucherFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22);

        PdfPTable table = new PdfPTable(new float[]{45, 10, 45});
        table.setWidthPercentage(100);

        PdfPCell issuer = cell();
        issuer.addElement(new Paragraph(settings.getLegalName(), title));
        issuer.addElement(new Paragraph(settings.getFiscalAddress(), normal));
        issuer.addElement(new Paragraph(settings.getCity() + ", " + settings.getProvince(), normal));
        issuer.addElement(new Paragraph("CUIT: " + formatCuit(settings.getCuit()), bold));
        issuer.addElement(new Paragraph("Condición IVA: " + issuerCondition(settings), normal));
        String grossIncome = grossIncomeDescription(settings);
        if (grossIncome != null) {
            issuer.addElement(new Paragraph("Ingresos Brutos: " + grossIncome, normal));
        }
        if (settings.getActivityStartDate() != null) {
            issuer.addElement(new Paragraph(
                    "Inicio de actividades: " + DATE.format(settings.getActivityStartDate()),
                    normal
            ));
        }

        PdfPCell voucher = cell();
        voucher.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph letter = new Paragraph(fiscal.getVoucherClass().name(), voucherFont);
        letter.setAlignment(Element.ALIGN_CENTER);
        voucher.addElement(letter);
        Paragraph code = new Paragraph("COD. " + String.format("%02d", fiscal.getVoucherTypeCode()), normal);
        code.setAlignment(Element.ALIGN_CENTER);
        voucher.addElement(code);

        PdfPCell data = cell();
        data.addElement(new Paragraph("FACTURA " + fiscal.getVoucherClass().name(), title));
        data.addElement(new Paragraph(
                String.format("%05d-%08d", fiscal.getPointOfSale(), fiscal.getVoucherNumber()),
                bold
        ));
        data.addElement(new Paragraph("Fecha: " + DATE.format(fiscal.getIssueDate()), normal));
        data.addElement(new Paragraph("Venta Anaquel: #" + fiscal.getSale().getId(), normal));

        table.addCell(issuer);
        table.addCell(voucher);
        table.addCell(data);
        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addRecipient(Document document, FiscalDocument fiscal) throws Exception {
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30, 70});

        addLabelValue(
                table,
                "Receptor",
                fiscal.getRecipientName() != null ? fiscal.getRecipientName() : "A CONSUMIDOR FINAL",
                bold,
                normal
        );
        addLabelValue(table, "Condición IVA", recipientCondition(fiscal.getRecipientVatCondition()), bold, normal);
        if (fiscal.getRecipientDocumentNumber() != null) {
            addLabelValue(
                    table,
                    fiscal.getRecipientDocumentType().name(),
                    fiscal.getRecipientDocumentNumber(),
                    bold,
                    normal
            );
        }
        addLabelValue(
                table,
                "Domicilio",
                fiscal.getRecipientAddress() != null ? fiscal.getRecipientAddress() : "NR",
                bold,
                normal
        );

        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addItems(Document document, List<SaleItem> items) throws Exception {
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        PdfPTable table = new PdfPTable(new float[]{55, 10, 17, 18});
        table.setWidthPercentage(100);
        addHeaderCell(table, "Descripción", bold);
        addHeaderCell(table, "Cant.", bold);
        addHeaderCell(table, "P. unitario", bold);
        addHeaderCell(table, "Subtotal", bold);

        for (SaleItem item : items) {
            table.addCell(textCell(item.getDescription(), normal, Element.ALIGN_LEFT));
            table.addCell(textCell(item.getQuantity().toString(), normal, Element.ALIGN_CENTER));
            table.addCell(textCell(money(item.getUnitPrice()), normal, Element.ALIGN_RIGHT));
            table.addCell(textCell(money(item.getSubtotal()), normal, Element.ALIGN_RIGHT));
        }

        document.add(table);
    }

    private void addTotals(Document document, FiscalDocument fiscal) throws Exception {
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

        PdfPTable table = new PdfPTable(new float[]{70, 30});
        table.setWidthPercentage(100);
        table.addCell(emptyCell());

        PdfPCell totals = cell();
        totals.addElement(right("Subtotal: " + money(fiscal.getSale().getSubtotal()), normal));
        if (fiscal.getSale().getDiscountAmount().signum() > 0) {
            totals.addElement(right("Descuento: -" + money(fiscal.getSale().getDiscountAmount()), normal));
        }
        totals.addElement(right("TOTAL: " + money(fiscal.getTotalAmount()), bold));
        totals.addElement(right(
                fiscal.getVoucherClass() == FiscalVoucherClass.C
                        ? "Comprobante C - IVA no discriminado. Venta de libros."
                        : "Operación exenta de IVA - venta de libros.",
                normal
        ));
        table.addCell(totals);

        document.add(table);

        if (fiscal.getVoucherClass() == FiscalVoucherClass.A
                && fiscal.getRecipientVatCondition() == RecipientVatCondition.MONOTRIBUTO) {
            document.add(new Paragraph(
                    "El crédito fiscal discriminado en el presente comprobante, solo podrá ser computado "
                            + "a efectos del Régimen de Sostenimiento e Inclusión Fiscal para Pequeños "
                            + "Contribuyentes de la Ley Nº 27.618.",
                    normal
            ));
        }

        document.add(new Paragraph(" "));
    }

    private void addPayments(Document document, List<SalePayment> payments) throws Exception {
        if (payments.isEmpty()) return;

        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        document.add(new Paragraph("Medios de pago", bold));

        for (SalePayment payment : payments) {
            String line = humanize(payment.getMethod().name()) + ": " + money(payment.getAmount());
            if (payment.getReference() != null) line += " - " + payment.getReference();
            document.add(new Paragraph(line, normal));
        }
        document.add(new Paragraph(" "));
    }

    private void addFiscalFooter(Document document, FiscalDocument fiscal) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{25, 75});
        table.setWidthPercentage(100);

        Image qr = createQrImage(fiscal.getQrUrl());
        qr.scaleToFit(110, 110);

        PdfPCell qrCell = cell();
        qrCell.addElement(qr);
        table.addCell(qrCell);

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        PdfPCell fiscalData = cell();
        fiscalData.addElement(new Paragraph("ARCA", title));
        fiscalData.addElement(new Paragraph("Comprobante autorizado", bold));
        fiscalData.addElement(new Paragraph("CAE: " + fiscal.getCae(), normal));
        if (fiscal.getCaeExpirationDate() != null) {
            fiscalData.addElement(new Paragraph(
                    "Vencimiento CAE: " + DATE.format(fiscal.getCaeExpirationDate()),
                    normal
            ));
        }
        fiscalData.addElement(new Paragraph(
                "Emitido por Anaquel el " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                        .format(fiscal.getAuthorizedAt()),
                normal
        ));
        table.addCell(fiscalData);

        document.add(table);
    }


    private Image createQrImage(String value) throws WriterException, IOException, BadElementException {
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 260, 260);
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return Image.getInstance(output.toByteArray());
        }
    }

    private PdfPCell cell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setPadding(8);
        return cell;
    }

    private PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = textCell(value, font, Element.ALIGN_CENTER);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private PdfPCell textCell(String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        return cell;
    }

    private void addLabelValue(
            PdfPTable table,
            String label,
            String value,
            Font labelFont,
            Font valueFont
    ) {
        table.addCell(textCell(label, labelFont, Element.ALIGN_LEFT));
        table.addCell(textCell(value, valueFont, Element.ALIGN_LEFT));
    }

    private Paragraph right(String value, Font font) {
        Paragraph paragraph = new Paragraph(value, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ARGENTINA);
        return format.format(value);
    }

    private String humanize(String value) {
        return value.replace('_', ' ').toLowerCase(ARGENTINA);
    }

    private String grossIncomeDescription(BookstoreFiscalSettings settings) {
        GrossIncomeRegime regime = settings.getGrossIncomeRegime();
        String number = settings.getGrossIncomeNumber();

        if (regime == null) return number;

        return switch (regime) {
            case LOCAL -> number == null ? "Régimen local" : "Local - " + number;
            case MULTILATERAL_AGREEMENT -> number == null
                    ? "Convenio Multilateral"
                    : "Convenio Multilateral - " + number;
            case EXEMPT -> "Exento";
            case NOT_REGISTERED -> "No inscripto";
        };
    }

    private String issuerCondition(BookstoreFiscalSettings settings) {
        return switch (settings.getTaxCondition()) {
            case IVA_RESPONSABLE_INSCRIPTO -> "IVA RESPONSABLE INSCRIPTO";
            case MONOTRIBUTO -> "RESPONSABLE MONOTRIBUTO";
            case IVA_EXENTO -> "IVA EXENTO";
        };
    }

    private String recipientCondition(RecipientVatCondition condition) {
        return switch (condition) {
            case IVA_RESPONSABLE_INSCRIPTO -> "IVA RESPONSABLE INSCRIPTO";
            case MONOTRIBUTO -> "RESPONSABLE MONOTRIBUTO";
            case IVA_EXENTO -> "IVA EXENTO";
            case CONSUMIDOR_FINAL -> "CONSUMIDOR FINAL";
        };
    }

    private String formatCuit(String cuit) {
        if (cuit == null || cuit.length() != 11) return cuit;
        return cuit.substring(0, 2) + "-" + cuit.substring(2, 10) + "-" + cuit.substring(10);
    }
}
