package com.rodrilang.librarymanager.fiscal.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
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
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.config.ArcaEnvironment;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.model.GrossIncomeRegime;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;
import com.rodrilang.librarymanager.fiscal.repository.BookstoreFiscalSettingsRepository;
import com.rodrilang.librarymanager.fiscal.repository.FiscalDocumentRepository;
import com.rodrilang.librarymanager.fiscal.service.FiscalPdfService;
import com.rodrilang.librarymanager.sales.model.PaymentMethod;
import com.rodrilang.librarymanager.sales.model.SaleItem;
import com.rodrilang.librarymanager.sales.model.SalePayment;
import com.rodrilang.librarymanager.sales.repository.SaleItemRepository;
import com.rodrilang.librarymanager.sales.repository.SalePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FiscalPdfServiceImpl implements FiscalPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private static final float PAGE_MARGIN = 34f;
    private static final float CARD_RADIUS = 8f;
    private static final float CARD_BORDER_WIDTH = 0.7f;
    private static final float SECTION_GAP = 10f;

    private static final Color TEXT = new Color(31, 41, 55);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color BORDER = new Color(218, 222, 228);
    private static final Color SOFT = new Color(245, 247, 249);
    private static final Color SOFT_ALT = new Color(250, 251, 252);
    private static final Color WHITE = Color.WHITE;

    private final FiscalDocumentRepository documentRepository;
    private final BookstoreFiscalSettingsRepository settingsRepository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final BookstoreContext bookstoreContext;
    private final ArcaProperties arcaProperties;

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
            Document document = new Document(
                    PageSize.A4,
                    PAGE_MARGIN,
                    PAGE_MARGIN,
                    30,
                    28
            );
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.open();

            addEnvironmentBanner(document);
            addHeader(document, fiscal, settings);
            addRecipient(document, fiscal);
            addItems(document, items);
            addBottomSection(document, writer, fiscal, payments);

            document.close();
            return output.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("No se pudo generar el PDF del comprobante.");
        }
    }

    private void addEnvironmentBanner(Document document) throws Exception {
        if (arcaProperties.environment() != ArcaEnvironment.HOMOLOGATION) return;

        PdfPTable table = fullWidthTable(1);
        table.setSpacingAfter(12);

        PdfPCell cell = roundedCell(SOFT, BORDER, 7);
        cell.setPaddingTop(7);
        cell.setPaddingBottom(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.addElement(centered(
                "HOMOLOGACIÓN - COMPROBANTE DE PRUEBA - SIN VALIDEZ FISCAL",
                font(8.5f, true, TEXT)
        ));

        table.addCell(cell);
        document.add(table);
    }

    private void addHeader(
            Document document,
            FiscalDocument fiscal,
            BookstoreFiscalSettings settings
    ) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{58, 2.5f, 39.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        PdfPCell issuer = roundedCell(WHITE, BORDER, 12);

        String commercialName = settings.getBookstore().getName();
        String legalName = settings.getLegalName();

        issuer.addElement(new Paragraph(
                hasText(commercialName) ? commercialName.trim() : legalName,
                font(15, true, TEXT)
        ));

        if (hasText(commercialName)
                && hasText(legalName)
                && !commercialName.trim().equalsIgnoreCase(legalName.trim())) {
            issuer.addElement(spaced(
                    legalName.trim(),
                    8.5f,
                    true,
                    TEXT,
                    3
            ));
        }

        issuer.addElement(spaced(
                "Domicilio comercial: " + settings.getFiscalAddress(),
                8.5f,
                false,
                TEXT,
                4
        ));

        issuer.addElement(spaced(
                settings.getCity() + ", " + settings.getProvince(),
                8.5f,
                false,
                TEXT,
                1
        ));

        issuer.addElement(spaced(
                "CUIT: " + formatCuit(settings.getCuit()),
                8.5f,
                true,
                TEXT,
                5
        ));

        issuer.addElement(spaced(
                "Condición IVA: " + issuerCondition(settings),
                8.2f,
                false,
                TEXT,
                1
        ));

        String grossIncome = grossIncomeDescription(settings);
        if (grossIncome != null) {
            issuer.addElement(spaced(
                    "Ingresos Brutos: " + grossIncome,
                    8.2f,
                    false,
                    TEXT,
                    1
            ));
        }

        if (settings.getActivityStartDate() != null) {
            issuer.addElement(spaced(
                    "Inicio de actividades: " + DATE.format(settings.getActivityStartDate()),
                    8.2f,
                    false,
                    TEXT,
                    1
            ));
        }

        table.addCell(issuer);
        table.addCell(gapCell());
        table.addCell(voucherCard(fiscal));

        document.add(table);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PdfPCell voucherCard(FiscalDocument fiscal) throws Exception {
        PdfPCell card = roundedCell(SOFT, BORDER, 10);

        PdfPTable content = new PdfPTable(new float[]{28, 72});
        content.setWidthPercentage(100);

        PdfPCell badgeColumn = borderlessCell(0);
        badgeColumn.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(72);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell letter = roundedCell(WHITE, BORDER, 0);
        letter.setPhrase(new Phrase(
                fiscal.getVoucherClass().name(),
                font(24, true, TEXT)
        ));
        letter.setHorizontalAlignment(Element.ALIGN_CENTER);
        letter.setVerticalAlignment(Element.ALIGN_MIDDLE);
        letter.setMinimumHeight(44f);
        letter.setPadding(0);
        badge.addCell(letter);

        badgeColumn.addElement(badge);

        Paragraph code = centered(
                "COD. " + String.format("%03d", fiscal.getVoucherTypeCode()),
                font(7.3f, true, MUTED)
        );
        code.setSpacingBefore(4);
        badgeColumn.addElement(code);

        PdfPCell data = borderlessCell(0);
        data.setPaddingLeft(7);
        data.addElement(new Paragraph(
                "FACTURA " + fiscal.getVoucherClass().name(),
                font(14.5f, true, TEXT)
        ));
        data.addElement(spaced(
                String.format("%05d-%08d", fiscal.getPointOfSale(), fiscal.getVoucherNumber()),
                10,
                true,
                TEXT,
                4
        ));
        data.addElement(spaced(
                "Fecha: " + DATE.format(fiscal.getIssueDate()),
                8.2f,
                false,
                TEXT,
                4
        ));

        content.addCell(badgeColumn);
        content.addCell(data);

        card.addElement(content);
        return card;
    }

    private void addRecipient(Document document, FiscalDocument fiscal) throws Exception {
        PdfPTable wrapper = fullWidthTable(1);
        wrapper.setSpacingAfter(12);

        PdfPCell card = roundedCell(WHITE, BORDER, 10);
        card.addElement(sectionLabel("DATOS DEL RECEPTOR"));

        PdfPTable fields = new PdfPTable(new float[]{50, 50});
        fields.setWidthPercentage(100);
        fields.setSpacingBefore(4);

        fields.addCell(fieldCell(
                "Receptor",
                fiscal.getRecipientName() != null ? fiscal.getRecipientName() : "A CONSUMIDOR FINAL"
        ));
        fields.addCell(fieldCell(
                "Condición IVA",
                recipientCondition(fiscal.getRecipientVatCondition())
        ));

        if (fiscal.getRecipientDocumentNumber() != null) {
            fields.addCell(fieldCell(
                    fiscal.getRecipientDocumentType().name(),
                    fiscal.getRecipientDocumentNumber()
            ));
            fields.addCell(fieldCell(
                    "Domicilio",
                    fiscal.getRecipientAddress() != null ? fiscal.getRecipientAddress() : "NR"
            ));
        } else {
            PdfPCell address = fieldCell(
                    "Domicilio",
                    fiscal.getRecipientAddress() != null ? fiscal.getRecipientAddress() : "NR"
            );
            address.setColspan(2);
            fields.addCell(address);
        }

        card.addElement(fields);
        wrapper.addCell(card);
        document.add(wrapper);
    }

    private void addItems(Document document, List<SaleItem> items) throws Exception {
        PdfPTable wrapper = fullWidthTable(1);
        PdfPCell card = roundedCell(WHITE, BORDER, 0);
        card.setPadding(0);

        PdfPTable table = new PdfPTable(new float[]{47, 9, 9, 17, 18});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitRows(true);
        table.setSplitLate(false);

        addItemsHeader(table, "Descripción", Element.ALIGN_LEFT);
        addItemsHeader(table, "Cant.", Element.ALIGN_CENTER);
        addItemsHeader(table, "Unidad", Element.ALIGN_CENTER);
        addItemsHeader(table, "P. unitario", Element.ALIGN_RIGHT);
        addItemsHeader(table, "Subtotal", Element.ALIGN_RIGHT);

        for (int index = 0; index < items.size(); index++) {
            SaleItem item = items.get(index);
            Color rowBackground = index % 2 == 0 ? WHITE : SOFT_ALT;

            table.addCell(itemCell(item.getDescription(), Element.ALIGN_LEFT, rowBackground));
            table.addCell(itemCell(item.getQuantity().toString(), Element.ALIGN_CENTER, rowBackground));
            table.addCell(itemCell("ud", Element.ALIGN_CENTER, rowBackground));
            table.addCell(itemCell(money(item.getUnitPrice()), Element.ALIGN_RIGHT, rowBackground));
            table.addCell(itemCell(money(item.getSubtotal()), Element.ALIGN_RIGHT, rowBackground));
        }

        card.addElement(table);
        wrapper.addCell(card);
        document.add(wrapper);
    }

    private void addBottomSection(
            Document document,
            PdfWriter writer,
            FiscalDocument fiscal,
            List<SalePayment> payments
    ) throws Exception {
        PdfPTable summary = buildSummary(fiscal, payments);
        PdfPTable footer = buildFiscalFooter(fiscal);

        float contentWidth = PageSize.A4.getWidth() - document.leftMargin() - document.rightMargin();
        float requiredHeight = measuredHeight(summary, contentWidth)
                + SECTION_GAP
                + measuredHeight(footer, contentWidth);
        float availableHeight = writer.getVerticalPosition(true) - document.bottomMargin();

        if (availableHeight < requiredHeight + 8) {
            document.newPage();
            availableHeight = writer.getVerticalPosition(true) - document.bottomMargin();
        }

        float spacerHeight = Math.max(12, availableHeight - requiredHeight);
        document.add(verticalSpacer(spacerHeight));
        document.add(summary);
        document.add(verticalSpacer(SECTION_GAP));
        document.add(footer);
    }

    private PdfPTable buildSummary(
            FiscalDocument fiscal,
            List<SalePayment> payments
    ) throws Exception {
        PdfPTable wrapper = fullWidthTable(1);
        wrapper.setKeepTogether(true);

        PdfPTable row = new PdfPTable(new float[]{57, 2.5f, 40.5f});
        row.setWidthPercentage(100);

        row.addCell(buildPaymentCard(payments));
        row.addCell(gapCell());

        PdfPCell totalsCard = roundedCell(SOFT, BORDER, 10);
        totalsCard.addElement(sectionLabel("RESUMEN"));

        PdfPTable totals = new PdfPTable(new float[]{52, 48});
        totals.setWidthPercentage(100);
        totals.setSpacingBefore(4);

        addTotalRow(
                totals,
                "Subtotal",
                money(fiscal.getSale().getSubtotal()),
                false
        );

        if (fiscal.getSale().getDiscountAmount().signum() > 0) {
            addTotalRow(
                    totals,
                    "Descuento",
                    "-" + money(fiscal.getSale().getDiscountAmount()),
                    false
            );
        }

        addTotalDivider(totals);
        addTotalRow(
                totals,
                "TOTAL",
                money(fiscal.getTotalAmount()),
                true
        );

        totalsCard.addElement(totals);
        row.addCell(totalsCard);

        PdfPCell rowCell = borderlessCell(0);
        rowCell.addElement(row);
        wrapper.addCell(rowCell);

        if (fiscal.getVoucherClass() == FiscalVoucherClass.A
                && fiscal.getRecipientVatCondition() == RecipientVatCondition.MONOTRIBUTO) {
            PdfPCell legalNote = borderlessCell(0);
            legalNote.setPaddingTop(7);
            legalNote.addElement(new Paragraph(
                    "El crédito fiscal discriminado en el presente comprobante, solo podrá ser computado "
                            + "a efectos del Régimen de Sostenimiento e Inclusión Fiscal para Pequeños "
                            + "Contribuyentes de la Ley Nº 27.618.",
                    font(7.1f, false, MUTED)
            ));
            wrapper.addCell(legalNote);
        }

        return wrapper;
    }

    private PdfPTable buildFiscalFooter(FiscalDocument fiscal) throws Exception {
        PdfPTable wrapper = fullWidthTable(1);
        wrapper.setKeepTogether(true);

        PdfPTable row = new PdfPTable(new float[]{57, 2.5f, 40.5f});
        row.setWidthPercentage(100);

        row.addCell(buildTransparencyCard(fiscal));
        row.addCell(gapCell());
        row.addCell(buildArcaCard(fiscal));

        PdfPCell rowCell = borderlessCell(0);
        rowCell.addElement(row);

        wrapper.addCell(rowCell);
        return wrapper;
    }

    private PdfPCell buildTransparencyCard(FiscalDocument fiscal) throws Exception {
        PdfPCell card = roundedCell(WHITE, BORDER, 10);

        PdfPTable content = new PdfPTable(new float[]{24, 76});
        content.setWidthPercentage(100);

        Image qr = createQrImage(fiscal.getQrUrl());
        qr.scaleToFit(78, 78);
        qr.setAlignment(Element.ALIGN_CENTER);

        PdfPCell qrCell = borderlessCell(0);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.addElement(qr);

        PdfPCell transparency = borderlessCell(0);
        transparency.setPaddingLeft(8);
        transparency.setVerticalAlignment(Element.ALIGN_MIDDLE);

        transparency.addElement(new Paragraph(
                "RÉGIMEN DE TRANSPARENCIA FISCAL AL CONSUMIDOR",
                font(6.8f, true, MUTED)
        ));

        transparency.addElement(spaced(
                "Ley 27.743",
                7.2f,
                false,
                MUTED,
                2
        ));

        PdfPTable taxes = new PdfPTable(new float[]{72, 28});
        taxes.setWidthPercentage(100);
        taxes.setSpacingBefore(6);

        addTransparencyRow(
                taxes,
                "IVA Contenido",
                money(transparencyVatAmount(fiscal))
        );

        addTransparencyRow(
                taxes,
                "Otros Impuestos Nacionales Indirectos",
                money(otherNationalIndirectTaxes(fiscal))
        );

        transparency.addElement(taxes);

        content.addCell(qrCell);
        content.addCell(transparency);
        card.addElement(content);

        return card;
    }

    private void addTransparencyRow(
            PdfPTable table,
            String label,
            String value
    ) {
        PdfPCell labelCell = borderlessCell(1);
        labelCell.addElement(new Paragraph(
                label,
                font(7.1f, false, TEXT)
        ));

        PdfPCell valueCell = borderlessCell(1);
        Paragraph amount = new Paragraph(
                value,
                font(7.2f, true, TEXT)
        );
        amount.setAlignment(Element.ALIGN_RIGHT);
        valueCell.addElement(amount);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private BigDecimal transparencyVatAmount(FiscalDocument fiscal) {
        return BigDecimal.ZERO;
    }

    private BigDecimal otherNationalIndirectTaxes(FiscalDocument fiscal) {
        return BigDecimal.ZERO;
    }

    private PdfPCell buildArcaCard(FiscalDocument fiscal) {
        PdfPCell card = roundedCell(SOFT, BORDER, 10);
        card.setVerticalAlignment(Element.ALIGN_MIDDLE);

        card.addElement(new Paragraph(
                "ARCA",
                font(14, true, TEXT)
        ));

        card.addElement(spaced(
                arcaProperties.environment() == ArcaEnvironment.HOMOLOGATION
                        ? "Comprobante autorizado en homologación"
                        : "Comprobante autorizado",
                8.2f,
                true,
                TEXT,
                3
        ));

        PdfPTable metadata = new PdfPTable(new float[]{50, 50});
        metadata.setWidthPercentage(100);
        metadata.setSpacingBefore(7);

        metadata.addCell(metadataCell(
                "CAE N.º",
                fiscal.getCae()
        ));

        metadata.addCell(metadataCell(
                "Vto. CAE",
                fiscal.getCaeExpirationDate() != null
                        ? DATE.format(fiscal.getCaeExpirationDate())
                        : "-"
        ));

        card.addElement(metadata);

        card.addElement(spaced(
                "Generado por Anaquel",
                7.2f,
                false,
                MUTED,
                7
        ));

        return card;
    }

    private void addItemsHeader(PdfPTable table, String value, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font(7.8f, true, TEXT)));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(SOFT);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.8f);
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(8);
        cell.setPaddingRight(8);
        table.addCell(cell);
    }

    private PdfPCell itemCell(String value, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font(8.3f, false, TEXT)));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(background);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(8);
        cell.setPaddingRight(8);
        return cell;
    }

    private PdfPCell fieldCell(String label, String value) {
        PdfPCell cell = borderlessCell(3);
        cell.setPaddingTop(4);
        cell.setPaddingBottom(4);
        cell.addElement(new Paragraph(label.toUpperCase(ARGENTINA), font(6.6f, true, MUTED)));
        cell.addElement(spaced(value, 8.7f, false, TEXT, 2));
        return cell;
    }

    private void addTotalRow(PdfPTable table, String label, String value, boolean total) {
        Font labelFont = font(total ? 9.5f : 8.2f, total, total ? TEXT : MUTED);
        Font valueFont = font(total ? 13 : 8.8f, true, TEXT);

        PdfPCell labelCell = borderlessCell(2);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.addElement(new Paragraph(label, labelFont));

        PdfPCell valueCell = borderlessCell(2);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph paragraph = new Paragraph(value, valueFont);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        valueCell.addElement(paragraph);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addTotalDivider(PdfPTable table) {
        PdfPCell divider = new PdfPCell(new Phrase(""));
        divider.setColspan(2);
        divider.setBorder(Rectangle.TOP);
        divider.setBorderColor(BORDER);
        divider.setBorderWidth(0.7f);
        divider.setFixedHeight(5);
        divider.setPadding(0);
        table.addCell(divider);
    }

    private PdfPCell metadataCell(String label, String value) {
        PdfPCell cell = borderlessCell(2);
        cell.addElement(new Paragraph(label.toUpperCase(ARGENTINA), font(6.4f, true, MUTED)));
        cell.addElement(spaced(value, 8.2f, true, TEXT, 2));
        return cell;
    }

    private Paragraph sectionLabel(String value) {
        Paragraph paragraph = new Paragraph(value, font(6.8f, true, MUTED));
        paragraph.setSpacingAfter(1);
        return paragraph;
    }

    private Paragraph centered(String value, Font font) {
        Paragraph paragraph = new Paragraph(value, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private Paragraph spaced(
            String value,
            float size,
            boolean bold,
            Color color,
            float spacingBefore
    ) {
        Paragraph paragraph = new Paragraph(value, font(size, bold, color));
        paragraph.setSpacingBefore(spacingBefore);
        return paragraph;
    }

    private Font font(float size, boolean bold, Color color) {
        Font font = FontFactory.getFont(
                bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA,
                size
        );
        font.setColor(color);
        return font;
    }

    private PdfPTable fullWidthTable(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private PdfPCell roundedCell(Color background, Color border, float padding) {
        PdfPCell cell = borderlessCell(padding);
        cell.setCellEvent(new RoundedCellEvent(background, border, CARD_RADIUS, CARD_BORDER_WIDTH));
        return cell;
    }

    private PdfPCell borderlessCell(float padding) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(padding);
        return cell;
    }

    private PdfPCell gapCell() {
        PdfPCell cell = borderlessCell(0);
        cell.setBackgroundColor(WHITE);
        return cell;
    }

    private PdfPTable verticalSpacer(float height) {
        PdfPTable spacer = fullWidthTable(1);
        PdfPCell cell = borderlessCell(0);
        cell.setFixedHeight(Math.max(0, height));
        spacer.addCell(cell);
        return spacer;
    }

    private float measuredHeight(PdfPTable table, float width) {
        table.setTotalWidth(width);
        table.setLockedWidth(true);
        table.calculateHeights(true);
        return table.getTotalHeight();
    }

    private Image createQrImage(String value) throws WriterException, IOException, BadElementException {

        if (value == null || value.isBlank()) {
            throw new BusinessException("El comprobante autorizado no tiene generado el código QR fiscal.");
        }

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

    private String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ARGENTINA);
        return format.format(value);
    }

    private String paymentMethodLabel(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Efectivo";
            case DEBIT_CARD -> "Tarjeta de débito";
            case CREDIT_CARD -> "Tarjeta de crédito";
            case TRANSFER -> "Transferencia";
            case DIGITAL_WALLET -> "Billetera virtual";
            case OTHER -> "Otro";
        };
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
            case CONSUMIDOR_FINAL -> "A CONSUMIDOR FINAL";
        };
    }

    private String formatCuit(String cuit) {
        if (cuit == null || cuit.length() != 11) return cuit;
        return cuit.substring(0, 2) + "-" + cuit.substring(2, 10) + "-" + cuit.substring(10);
    }

    private static final class RoundedCellEvent implements PdfPCellEvent {

        private final Color background;
        private final Color border;
        private final float radius;
        private final float borderWidth;

        private RoundedCellEvent(
                Color background,
                Color border,
                float radius,
                float borderWidth
        ) {
            this.background = background;
            this.border = border;
            this.radius = radius;
            this.borderWidth = borderWidth;
        }

        @Override
        public void cellLayout(
                PdfPCell cell,
                Rectangle position,
                PdfContentByte[] canvases
        ) {
            float inset = Math.max(0.5f, borderWidth / 2f);
            float x = position.getLeft() + inset;
            float y = position.getBottom() + inset;
            float width = position.getWidth() - inset * 2;
            float height = position.getHeight() - inset * 2;

            PdfContentByte backgroundCanvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            backgroundCanvas.saveState();
            backgroundCanvas.setColorFill(background);
            backgroundCanvas.roundRectangle(x, y, width, height, radius);
            backgroundCanvas.fill();
            backgroundCanvas.restoreState();

            if (borderWidth <= 0) return;

            PdfContentByte lineCanvas = canvases[PdfPTable.LINECANVAS];
            lineCanvas.saveState();
            lineCanvas.setColorStroke(border);
            lineCanvas.setLineWidth(borderWidth);
            lineCanvas.roundRectangle(x, y, width, height, radius);
            lineCanvas.stroke();
            lineCanvas.restoreState();
        }
    }

    private PdfPCell buildPaymentCard(List<SalePayment> payments) {
        PdfPCell card = roundedCell(WHITE, BORDER, 10);

        card.addElement(sectionLabel("CONDICIÓN DE VENTA"));
        card.addElement(spaced(
                "Contado",
                8.5f,
                true,
                TEXT,
                4
        ));

        if (!payments.isEmpty()) {
            card.addElement(spaced(
                    "MEDIOS DE PAGO",
                    6.8f,
                    true,
                    MUTED,
                    10
            ));

            for (SalePayment payment : payments) {
                String line = paymentMethodLabel(payment.getMethod())
                        + ": "
                        + money(payment.getAmount());

                if (payment.getReference() != null) {
                    line += " - " + payment.getReference();
                }

                card.addElement(spaced(
                        line,
                        8.5f,
                        false,
                        TEXT,
                        5
                ));
            }
        }

        return card;
    }
}
