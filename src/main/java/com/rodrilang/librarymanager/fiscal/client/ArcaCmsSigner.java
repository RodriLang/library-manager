package com.rodrilang.librarymanager.fiscal.client;

import com.rodrilang.librarymanager.fiscal.exception.ArcaApiException;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ArcaCmsSigner {

    private final ArcaCredentialProvider credentials;

    public String sign(String content) {
        try {
            X509Certificate certificate = credentials.certificate();
            PrivateKey privateKey = credentials.privateKey();

            ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA")
                    .build(privateKey);

            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().build()
                    ).build(signer, certificate)
            );
            generator.addCertificates(new JcaCertStore(List.of(certificate)));

            CMSSignedData signed = generator.generate(
                    new CMSProcessableByteArray(content.getBytes(StandardCharsets.UTF_8)),
                    true
            );

            return Base64.getEncoder().encodeToString(signed.getEncoded());
        } catch (Exception exception) {
            throw new ArcaApiException("No se pudo firmar la solicitud de autenticación de ARCA.", exception);
        }
    }
}
