package com.rodrilang.librarymanager.fiscal.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.config.ArcaEnvironment;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import com.rodrilang.librarymanager.fiscal.repository.BookstoreFiscalSettingsRepository;
import com.rodrilang.librarymanager.fiscal.repository.FiscalDocumentRepository;
import com.rodrilang.librarymanager.fiscal.service.FiscalTicketService;
import com.rodrilang.librarymanager.payment.model.PaymentMethod;
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
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FiscalTicketServiceImpl implements FiscalTicketService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private final FiscalDocumentRepository documentRepository;
    private final BookstoreFiscalSettingsRepository settingsRepository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final BookstoreContext bookstoreContext;
    private final ArcaProperties arcaProperties;

    @Override
    @Transactional(readOnly = true)
    public String generate(Long documentId, boolean autoPrint) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        FiscalDocument fiscal = documentRepository.findByIdAndBookstoreId(documentId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el comprobante fiscal con ID: " + documentId
                ));

        if (fiscal.getStatus() != FiscalDocumentStatus.AUTHORIZED) {
            throw new BusinessException("Sólo se puede imprimir el ticket de un comprobante autorizado por ARCA.");
        }

        BookstoreFiscalSettings settings = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseThrow(() -> new BusinessException("No existe configuración fiscal."));

        if (!settings.isTicketPrintingEnabled()) {
            throw new BusinessException("La impresión de tickets no está habilitada para esta librería.");
        }

        List<SaleItem> items = itemRepository.findAllBySaleIdOrderByIdAsc(fiscal.getSale().getId());
        List<SalePayment> payments = paymentRepository.findAllBySaleIdOrderByIdAsc(fiscal.getSale().getId());
        return render(fiscal, settings, items, payments, autoPrint);
    }

    private String render(
            FiscalDocument fiscal,
            BookstoreFiscalSettings settings,
            List<SaleItem> items,
            List<SalePayment> payments,
            boolean autoPrint
    ) {
        int width = settings.getTicketPaperWidthMm() == null ? 80 : settings.getTicketPaperWidthMm();
        int margin = settings.getTicketMarginMm() == null ? 2 : settings.getTicketMarginMm();
        String label = fiscal.getDocumentType() == FiscalDocumentType.CREDIT_NOTE ? "NOTA DE CRÉDITO" : "FACTURA";
        String number = String.format("%05d-%08d", fiscal.getPointOfSale(), fiscal.getVoucherNumber());
        StringBuilder html = new StringBuilder(12000);

        html.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><title>")
                .append(escape(label)).append(' ').append(escape(number)).append("</title><style>")
                .append("@page{size:").append(width).append("mm auto;margin:0}")
                .append("*{box-sizing:border-box}html,body{margin:0;padding:0;background:#fff;color:#000;font-family:Arial,sans-serif}")
                .append("body{width:").append(width).append("mm;padding:").append(margin).append("mm;font-size:")
                .append(width == 58 ? "10.5px" : "12px").append(";line-height:1.25}")
                .append("h1,h2,p{margin:0}.center{text-align:center}.store{font-size:1.25em;font-weight:800}.legal{margin-top:2px;font-weight:700}")
                .append(".muted{font-size:.9em}.sep{border-top:1px dashed #000;margin:7px 0}.title{font-size:1.2em;font-weight:900;margin:4px 0}")
                .append(".row{display:flex;justify-content:space-between;gap:8px}.row span:first-child{min-width:0}.row strong{text-align:right;white-space:nowrap}")
                .append(".item{margin:6px 0}.desc{font-weight:700;overflow-wrap:anywhere}.item-line{display:flex;justify-content:space-between;gap:6px;font-size:.95em}")
                .append(".total{font-size:1.25em;font-weight:900;margin-top:4px}.qr{display:block;width:")
                .append(width == 58 ? "30mm" : "34mm").append(";height:auto;margin:7px auto 3px}.reason{margin-top:5px;font-size:.9em}")
                .append(".no-print{margin:12px auto 0;display:block;padding:8px 14px;font:inherit}@media print{.no-print{display:none}body{padding:")
                .append(margin).append("mm}}")
                .append("</style></head><body>");

        if (arcaProperties.environment() == ArcaEnvironment.HOMOLOGATION) {
            html.append("<div class=\"center\"><strong>HOMOLOGACIÓN - SIN VALIDEZ FISCAL</strong></div><div class=\"sep\"></div>");
        }

        String commercialName = settings.getBookstore().getName();
        html.append("<div class=\"center\"><div class=\"store\">").append(escape(textOr(commercialName, settings.getLegalName())))
                .append("</div>");
        if (hasText(commercialName) && hasText(settings.getLegalName()) && !commercialName.trim().equalsIgnoreCase(settings.getLegalName().trim())) {
            html.append("<div class=\"legal\">").append(escape(settings.getLegalName())).append("</div>");
        }
        html.append("<div class=\"muted\">CUIT: ").append(escape(formatCuit(settings.getCuit()))).append("</div>")
                .append("<div class=\"muted\">").append(escape(settings.getFiscalAddress())).append("</div>")
                .append("<div class=\"muted\">").append(escape(settings.getCity())).append(", ").append(escape(settings.getProvince())).append("</div></div>")
                .append("<div class=\"sep\"></div><div class=\"center title\">").append(label).append(' ').append(fiscal.getVoucherClass().name()).append("</div>")
                .append("<div class=\"center\"><strong>").append(number).append("</strong></div>")
                .append("<div class=\"center muted\">").append(DATE.format(fiscal.getIssueDate())).append("</div><div class=\"sep\"></div>");

        html.append("<div><strong>").append(escape(textOr(fiscal.getRecipientName(), "Consumidor final"))).append("</strong></div>");
        if (hasText(fiscal.getRecipientDocumentNumber())) {
            html.append("<div class=\"muted\">").append(escape(fiscal.getRecipientDocumentType().name())).append(": ")
                    .append(escape(fiscal.getRecipientDocumentNumber())).append("</div>");
        }
        if (hasText(fiscal.getRecipientAddress())) {
            html.append("<div class=\"muted\">").append(escape(fiscal.getRecipientAddress())).append("</div>");
        }
        html.append("<div class=\"sep\"></div>");

        for (SaleItem item : items) {
            html.append("<div class=\"item\"><div class=\"desc\">").append(escape(item.getDescription())).append("</div>")
                    .append("<div class=\"item-line\"><span>").append(item.getQuantity()).append(" x ").append(money(item.getUnitPrice()))
                    .append("</span><strong>").append(money(item.getSubtotal())).append("</strong></div></div>");
        }

        html.append("<div class=\"sep\"></div>")
                .append("<div class=\"row total\"><span>TOTAL</span><strong>").append(money(fiscal.getTotalAmount())).append("</strong></div>");

        if (!payments.isEmpty()) {
            html.append("<div class=\"sep\"></div><div><strong>Medio de pago</strong></div>");
            for (SalePayment payment : payments) {
                html.append("<div class=\"row muted\"><span>").append(escape(paymentLabel(payment.getMethod())))
                        .append("</span><strong>").append(money(payment.getAmount())).append("</strong></div>");
            }
        }

        if (fiscal.getDocumentType() == FiscalDocumentType.CREDIT_NOTE && hasText(fiscal.getReason())) {
            html.append("<div class=\"reason\"><strong>Motivo:</strong> ").append(escape(fiscal.getReason())).append("</div>");
        }

        html.append("<div class=\"sep\"></div><div class=\"center muted\">CAE: ").append(escape(fiscal.getCae())).append("</div>");
        if (fiscal.getCaeExpirationDate() != null) {
            html.append("<div class=\"center muted\">Vto. CAE: ").append(DATE.format(fiscal.getCaeExpirationDate())).append("</div>");
        }
        if (hasText(fiscal.getQrUrl())) {
            html.append("<img class=\"qr\" alt=\"QR ARCA\" src=\"").append(qrDataUrl(fiscal.getQrUrl())).append("\">");
        }
        html.append("<div class=\"center muted\">Comprobante autorizado por ARCA</div>")
                .append("<button class=\"no-print\" onclick=\"window.print()\">Imprimir ticket</button>");

        if (autoPrint) {
            html.append("<script>window.addEventListener('load',()=>setTimeout(()=>window.print(),250));</script>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String qrDataUrl(String value) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 260, 260);
            BufferedImage image = new BufferedImage(260, 260, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 260; y++) {
                for (int x = 0; x < 260; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception exception) {
            throw new BusinessException("No se pudo generar el QR del ticket.");
        }
    }

    private String paymentLabel(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Efectivo";
            case DEBIT_CARD -> "Tarjeta de débito";
            case CREDIT_CARD -> "Tarjeta de crédito";
            case TRANSFER -> "Transferencia";
            case DIGITAL_WALLET -> "Billetera digital";
            case OTHER -> "Otro";
        };
    }

    private String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ARGENTINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private String formatCuit(String value) {
        if (value == null || value.length() != 11) return textOr(value, "");
        return value.substring(0, 2) + "-" + value.substring(2, 10) + "-" + value.substring(10);
    }

    private String textOr(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
