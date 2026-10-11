package com.rodrilang.librarymanager.email.template;

import com.rodrilang.librarymanager.email.config.EmailProperties;
import com.rodrilang.librarymanager.notification.model.NotificationEntityType;
import com.rodrilang.librarymanager.notification.service.NotificationDeliveryOutboxService.DeliveryPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Year;

@Component
@RequiredArgsConstructor
public class NotificationEmailTemplateBuilder {

    private final EmailProperties properties;

    public String subject(DeliveryPayload payload) {
        return payload.title() + " · " + payload.bookstoreName();
    }

    public String html(DeliveryPayload payload) {
        String url = destination(payload);
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;color:#222222;">
                  <div style="max-width:620px;margin:0 auto;padding:32px 16px;">
                    <div style="background:#ffffff;border-radius:16px;padding:30px;">
                      <div style="font-size:13px;font-weight:700;color:#666666;margin-bottom:8px;">%s</div>
                      <h1 style="font-size:24px;line-height:1.25;margin:0 0 16px;">%s</h1>
                      <p style="font-size:16px;line-height:1.6;margin:0 0 24px;">%s</p>
                      <div style="text-align:center;margin-top:26px;">
                        <a href="%s" style="display:inline-block;background:#242424;color:#ffffff;text-decoration:none;padding:13px 22px;border-radius:9px;font-weight:700;">Ver en Anaquel</a>
                      </div>
                    </div>
                    <div style="text-align:center;padding:20px;color:#888888;font-size:12px;">© %d Anaquel</div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(payload.bookstoreName()),
                escape(payload.title()),
                escape(payload.message()),
                escape(url),
                Year.now().getValue()
        );
    }

    public String plainText(DeliveryPayload payload) {
        return "%s\n\n%s\n\n%s\n\nVer en Anaquel: %s"
                .formatted(payload.bookstoreName(), payload.title(), payload.message(), destination(payload));
    }

    private String destination(DeliveryPayload payload) {
        String path;
        if (payload.entityType() == NotificationEntityType.STORE_ORDER && payload.entityId() != null) {
            path = "/store/orders/" + payload.entityId();
        } else if (payload.entityType() == NotificationEntityType.TIENDANUBE_ORDER) {
            path = "/sales";
        } else {
            path = "/";
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.frontendUrl()).path(path);
        if (payload.entityType() == NotificationEntityType.TIENDANUBE_ORDER) {
            builder.queryParam("origin", "TIENDANUBE");
        }
        return builder.build().encode().toUriString();
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
