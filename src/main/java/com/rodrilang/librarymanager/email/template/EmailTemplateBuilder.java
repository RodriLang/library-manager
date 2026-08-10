package com.rodrilang.librarymanager.email.template;

import org.springframework.stereotype.Component;

import java.time.Year;

@Component
public class EmailTemplateBuilder {

    public String buildBookstoreInvitationTemplate(
            String bookstoreName,
            String invitationUrl) {

        String safeBookstoreName = escape(bookstoreName);
        String safeInvitationUrl = escape(invitationUrl);

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">
                    <title>Invitación a Anaquel</title>
                </head>

                <body style="
                    margin: 0;
                    padding: 0;
                    background-color: #f5f5f5;
                    font-family: Arial, Helvetica, sans-serif;
                    color: #222222;
                ">

                    <div style="
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 40px 20px;
                    ">

                        <div style="
                            background-color: #ffffff;
                            border-radius: 14px;
                            padding: 40px;
                        ">

                            <h1 style="
                                margin: 0 0 24px;
                                font-size: 26px;
                                line-height: 1.2;
                            ">
                                Bienvenido a Anaquel
                            </h1>

                            <p style="
                                font-size: 16px;
                                line-height: 1.6;
                            ">
                                Fuiste invitado a crear tu cuenta para
                                <strong>%s</strong>.
                            </p>

                            <p style="
                                font-size: 16px;
                                line-height: 1.6;
                            ">
                                Al completar el registro, tu usuario quedará
                                asociado automáticamente a esta librería.
                            </p>

                            <div style="
                                text-align: center;
                                margin: 32px 0;
                            ">
                                <a
                                    href="%s"
                                    style="
                                        display: inline-block;
                                        padding: 14px 24px;
                                        background-color: #242424;
                                        color: #ffffff;
                                        text-decoration: none;
                                        border-radius: 8px;
                                        font-weight: 600;
                                    "
                                >
                                    Crear mi cuenta
                                </a>
                            </div>

                            <p style="
                                color: #666666;
                                font-size: 14px;
                                line-height: 1.5;
                            ">
                                Este enlace es personal y de un solo uso.
                                Una vez utilizado, dejará de ser válido.
                            </p>

                            <p style="
                                color: #666666;
                                font-size: 14px;
                                line-height: 1.5;
                            ">
                                Si no esperabas recibir esta invitación,
                                podés ignorar este correo.
                            </p>

                        </div>

                        <div style="
                            text-align: center;
                            padding: 24px;
                            color: #888888;
                            font-size: 12px;
                        ">
                            © %d Anaquel
                        </div>

                    </div>

                </body>
                </html>
                """
                .formatted(
                        safeBookstoreName,
                        safeInvitationUrl,
                        Year.now().getValue()
                );
    }

    public String buildBookstoreInvitationPlainText(
            String bookstoreName,
            String invitationUrl) {

        return """
                Bienvenido a Anaquel

                Fuiste invitado a crear tu cuenta para %s.

                Al completar el registro, tu usuario quedará asociado
                automáticamente a esta librería.

                Para crear tu cuenta ingresá al siguiente enlace:

                %s

                Este enlace es personal y de un solo uso.

                Si no esperabas recibir esta invitación, podés ignorar
                este correo.

                Anaquel
                """
                .formatted(
                        bookstoreName,
                        invitationUrl
                );
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}