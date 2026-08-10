package com.rodrilang.librarymanager.email.provider;

import com.rodrilang.librarymanager.email.config.EmailProperties;
import com.rodrilang.librarymanager.email.exception.EmailSendingException;
import com.rodrilang.librarymanager.email.model.EmailMessage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailtrapEmailProvider implements EmailProvider {

    private final RestTemplate emailRestTemplate;
    private final EmailProperties properties;

    @Override
    public void send(EmailMessage message) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.apiToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        MailtrapRequest request = MailtrapRequest.builder()
                .from(new MailtrapAddress(
                        properties.from(),
                        properties.fromName()
                ))
                .to(List.of(
                        new MailtrapAddress(
                                message.to(),
                                null
                        )
                ))
                .subject(message.subject())
                .html(message.htmlBody())
                .text(message.plainTextBody())
                .build();

        try {
            emailRestTemplate.postForEntity(
                    properties.apiUrl(),
                    new HttpEntity<>(request, headers),
                    Void.class
            );

            log.info(
                    "Mailtrap email sent recipient={} subject={}",
                    message.to(),
                    message.subject()
            );

        } catch (RestClientException e) {

            log.error(
                    "Mailtrap email failed recipient={} subject={}",
                    message.to(),
                    message.subject(),
                    e
            );

            throw new EmailSendingException(
                    message.to(),
                    message.subject(),
                    e
            );
        }
    }

    @Override
    public String getName() {
        return "Mailtrap";
    }

    @Builder
    private record MailtrapRequest(
            MailtrapAddress from,
            List<MailtrapAddress> to,
            String subject,
            String html,
            String text
    ) {
    }

    private record MailtrapAddress(
            String email,
            String name
    ) {
    }
}
