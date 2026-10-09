package com.rodrilang.librarymanager.email.template;

import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationEvent;
import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Year;
import java.util.Locale;

@Component
public class StoreOrderEmailTemplateBuilder {

    private static final Locale AR_LOCALE = Locale.forLanguageTag("es-AR");

    public String subject(StoreOrderNotificationEvent event) {
        return switch (event.type()) {
            case RECEIVED -> "Recibimos tu pedido " + event.orderNumber() + " · " + event.storeName();
            case CONFIRMED -> "Tu pedido " + event.orderNumber() + " fue confirmado · " + event.storeName();
            case READY_FOR_PICKUP -> "Tu pedido " + event.orderNumber() + " está listo para retirar";
            case CANCELLED -> "Tu pedido " + event.orderNumber() + " fue cancelado";
            case COMPLETED -> "Pedido " + event.orderNumber() + " entregado · " + event.storeName();
        };
    }

    public String html(StoreOrderNotificationEvent event) {
        String safeStore = escape(event.storeName());
        String safeCustomer = escape(event.customerName());
        String safeOrder = escape(event.orderNumber());
        String safeTracking = escape(event.trackingUrl());
        String safeMessage = escape(message(event));
        String cancellation = cancellationHtml(event);

        StringBuilder rows = new StringBuilder();
        for (StoreOrderNotificationEvent.Item item : event.items()) {
            rows.append("""
                    <tr>
                      <td style="padding:12px 0;border-bottom:1px solid #eeeeee;vertical-align:top;">
                        <strong style="display:block;color:#222222;">%s</strong>
                        %s
                      </td>
                      <td style="padding:12px 0 12px 16px;border-bottom:1px solid #eeeeee;text-align:right;white-space:nowrap;vertical-align:top;">
                        %d × %s<br>
                        <strong>%s</strong>
                      </td>
                    </tr>
                    """.formatted(
                    escape(item.title()),
                    item.isbn() == null || item.isbn().isBlank()
                            ? ""
                            : "<span style=\"color:#777777;font-size:12px;\">ISBN " + escape(item.isbn()) + "</span>",
                    item.quantity(),
                    money(item.unitPrice()),
                    money(item.subtotal())
            ));
        }

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;color:#222222;">
                  <div style="max-width:640px;margin:0 auto;padding:32px 16px;">
                    <div style="background:#ffffff;border-radius:16px;padding:32px;">
                      <div style="font-size:13px;font-weight:700;color:#666666;margin-bottom:8px;">%s</div>
                      <h1 style="font-size:26px;line-height:1.2;margin:0 0 20px;">%s</h1>
                      <p style="font-size:16px;line-height:1.6;margin:0 0 8px;">Hola %s,</p>
                      <p style="font-size:16px;line-height:1.6;margin:0 0 24px;">%s</p>

                      <div style="background:#f7f7f7;border-radius:12px;padding:16px 18px;margin:0 0 24px;">
                        <div style="color:#777777;font-size:12px;text-transform:uppercase;letter-spacing:.06em;">Pedido</div>
                        <strong style="font-size:18px;">%s</strong>
                      </div>

                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;margin-bottom:18px;">
                        %s
                      </table>

                      <div style="display:flex;justify-content:space-between;border-top:1px solid #dddddd;padding-top:16px;margin-top:4px;font-size:18px;">
                        <strong>Total</strong>
                        <strong>%s</strong>
                      </div>

                      %s

                      <div style="text-align:center;margin:30px 0 10px;">
                        <a href="%s" style="display:inline-block;background:#242424;color:#ffffff;text-decoration:none;padding:13px 22px;border-radius:9px;font-weight:700;">
                          Ver estado del pedido
                        </a>
                      </div>

                      <p style="color:#777777;font-size:12px;line-height:1.5;margin:24px 0 0;">
                        Este enlace permite consultar el pedido sin iniciar sesión. No lo compartas públicamente.
                      </p>
                    </div>
                    <div style="text-align:center;padding:20px;color:#888888;font-size:12px;">© %d Anaquel</div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(subject(event)),
                safeStore,
                escape(title(event.type())),
                safeCustomer,
                safeMessage,
                safeOrder,
                rows,
                money(event.total()),
                cancellation,
                safeTracking,
                Year.now().getValue()
        );
    }

    public String plainText(StoreOrderNotificationEvent event) {
        StringBuilder items = new StringBuilder();
        for (StoreOrderNotificationEvent.Item item : event.items()) {
            items.append("- ")
                    .append(item.title())
                    .append(" · ")
                    .append(item.quantity())
                    .append(" × ")
                    .append(money(item.unitPrice()))
                    .append(" = ")
                    .append(money(item.subtotal()))
                    .append('\n');
        }

        String cancellation = event.type() == StoreOrderNotificationType.CANCELLED
                && event.cancellationReason() != null
                && !event.cancellationReason().isBlank()
                ? "\nMotivo: " + event.cancellationReason().trim() + "\n"
                : "";

        return """
                %s

                Hola %s,

                %s

                Pedido: %s

                %s
                Total: %s
                %s
                Podés consultar el estado del pedido acá:
                %s

                %s
                """.formatted(
                event.storeName(),
                event.customerName(),
                message(event),
                event.orderNumber(),
                items,
                money(event.total()),
                cancellation,
                event.trackingUrl(),
                event.storeName()
        );
    }

    private String title(StoreOrderNotificationType type) {
        return switch (type) {
            case RECEIVED -> "Recibimos tu pedido";
            case CONFIRMED -> "Tu pedido fue confirmado";
            case READY_FOR_PICKUP -> "Tu pedido está listo para retirar";
            case CANCELLED -> "Tu pedido fue cancelado";
            case COMPLETED -> "Tu pedido fue entregado";
        };
    }

    private String message(StoreOrderNotificationEvent event) {
        return switch (event.type()) {
            case RECEIVED -> "Recibimos tu compra y reservamos los libros mientras la librería revisa el pedido.";
            case CONFIRMED -> "La librería confirmó tu pedido. Te avisaremos cuando los libros estén listos para retirar.";
            case READY_FOR_PICKUP -> "La preparación terminó. Ya podés acercarte a la librería para retirar tu pedido.";
            case CANCELLED -> "La librería canceló el pedido y los libros reservados volvieron a quedar disponibles.";
            case COMPLETED -> "Registramos la entrega de tu pedido. Gracias por comprar en " + event.storeName() + ".";
        };
    }

    private String cancellationHtml(StoreOrderNotificationEvent event) {
        if (event.type() != StoreOrderNotificationType.CANCELLED
                || event.cancellationReason() == null
                || event.cancellationReason().isBlank()) {
            return "";
        }
        return "<div style=\"margin-top:20px;padding:14px 16px;border-radius:10px;background:#fff4f4;color:#8a2424;\"><strong>Motivo:</strong> "
                + escape(event.cancellationReason().trim()) + "</div>";
    }

    private String money(BigDecimal value) {
        if (value == null) return "$ 0";
        NumberFormat format = NumberFormat.getCurrencyInstance(AR_LOCALE);
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
