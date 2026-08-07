package com.rodrilang.librarymanager.media.image;

import com.rodrilang.librarymanager.media.exception.InvalidImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

@Slf4j
@Component
public class JpegImageOptimizer implements ImageOptimizer {

    /*
     * Para JPEG intentamos llegar a este tamaño.
     * No es un límite duro: el límite real sigue estando
     * definido en ImageValidator.
     */
    private static final long TARGET_SIZE_BYTES =
            2L * 1024 * 1024;

    /*
     * Si no alcanzamos los 2 MB pero queda debajo de 5 MB,
     * aceptamos igualmente el mejor resultado.
     */
    private static final long MAX_ACCEPTABLE_SIZE_BYTES =
            5L * 1024 * 1024;

    private static final int[] MAX_DIMENSIONS = {
            1800,
            1600,
            1400,
            1200
    };

    private static final float[] QUALITIES = {
            0.90f,
            0.85f,
            0.80f,
            0.75f
    };

    @Override
    public OptimizedImage optimize(
            byte[] content,
            String filename,
            String contentType
    ) {
        validateContent(content);

        /*
         * PNG / WEBP/etc:
         *
         * No los convertimos a JPEG.
         * Si pesan menos de 5 MB ImageValidator los aceptará.
         * Si pesan más, por ahora serán rechazados.
         */
        if (!isJpeg(filename, contentType)) {
            return new OptimizedImage(
                    content,
                    filename,
                    false
            );
        }

        /*
         * JPEG pequeño:
         * tampoco tiene sentido recomprimirlo.
         */
        if (content.length <= TARGET_SIZE_BYTES) {
            return new OptimizedImage(
                    content,
                    filename,
                    false
            );
        }

        BufferedImage original =
                toRgb(readImage(content));

        byte[] bestResult = content;

        for (int maxDimension : MAX_DIMENSIONS) {

            BufferedImage resized =
                    resizeIfNeeded(
                            original,
                            maxDimension
                    );

            for (float quality : QUALITIES) {

                byte[] compressed =
                        writeJpeg(
                                resized,
                                quality
                        );

                if (compressed.length < bestResult.length) {
                    bestResult = compressed;
                }

                log.debug(
                        "Image optimization attempt "
                                + "filename={} "
                                + "maxDimension={} "
                                + "quality={} "
                                + "size={}KB",
                        filename,
                        maxDimension,
                        quality,
                        compressed.length / 1024
                );

                if (compressed.length <= TARGET_SIZE_BYTES) {
                    log.info(
                            "JPEG optimized "
                                    + "filename={} "
                                    + "original={}KB "
                                    + "optimized={}KB "
                                    + "maxDimension={} "
                                    + "quality={}",
                            filename,
                            content.length / 1024,
                            compressed.length / 1024,
                            maxDimension,
                            quality
                    );

                    return new OptimizedImage(
                            compressed,
                            ensureJpegFilename(filename),
                            true
                    );
                }
            }
        }

        /*
         * Quizás no llegó al target de 2 MB,
         * pero sigue siendo válido para nuestro límite de 5 MB.
         */
        if (bestResult.length <= MAX_ACCEPTABLE_SIZE_BYTES) {
            log.info(
                    "JPEG optimized below maximum "
                            + "filename={} "
                            + "original={}KB "
                            + "optimized={}KB",
                    filename,
                    content.length / 1024,
                    bestResult.length / 1024
            );

            return new OptimizedImage(
                    bestResult,
                    ensureJpegFilename(filename),
                    true
            );
        }

        throw new InvalidImageException(
                "La imagen sigue superando el tamaño máximo permitido "
                        + "después de optimizarla"
        );
    }

    private void validateContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException(
                    "La imagen no puede estar vacía"
            );
        }
    }

    private boolean isJpeg(
            String filename,
            String contentType
    ) {
        if (contentType != null && !contentType.isBlank()) {
            String normalizedContentType =
                    contentType
                            .split(";", 2)[0]
                            .trim()
                            .toLowerCase(Locale.ROOT);

            if (
                    normalizedContentType.equals("image/jpeg")
                            || normalizedContentType.equals("image/jpg")
            ) {
                return true;
            }

            /*
             * Si el servidor declara explícitamente PNG/WEBP,
             * confiamos en eso para no convertirlo.
             */
            if (normalizedContentType.startsWith("image/")) {
                return false;
            }
        }

        /*
         * Fallback al nombre del archivo cuando el servidor
         * devuelve application/octet-stream o ningún content-type.
         */
        if (filename == null || filename.isBlank()) {
            return false;
        }

        String normalizedFilename =
                filename.trim()
                        .toLowerCase(Locale.ROOT);

        return normalizedFilename.endsWith(".jpg")
                || normalizedFilename.endsWith(".jpeg");
    }

    private BufferedImage readImage(byte[] content) {
        try (
                ByteArrayInputStream input =
                        new ByteArrayInputStream(content)
        ) {
            BufferedImage image =
                    ImageIO.read(input);

            if (image == null) {
                throw new InvalidImageException(
                        "No se pudo interpretar la imagen"
                );
            }

            return image;

        } catch (IOException exception) {
            throw new InvalidImageException(
                    "No se pudo leer la imagen",
                    exception
            );
        }
    }

    private BufferedImage toRgb(
            BufferedImage source
    ) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }

        BufferedImage rgb =
                new BufferedImage(
                        source.getWidth(),
                        source.getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );

        Graphics2D graphics =
                rgb.createGraphics();

        try {

            graphics.setColor(Color.WHITE);

            graphics.fillRect(
                    0,
                    0,
                    rgb.getWidth(),
                    rgb.getHeight()
            );

            graphics.drawImage(
                    source,
                    0,
                    0,
                    null
            );
        } finally {
            graphics.dispose();
        }

        return rgb;
    }

    private BufferedImage resizeIfNeeded(
            BufferedImage source,
            int maxDimension
    ) {
        int width = source.getWidth();
        int height = source.getHeight();

        int largestDimension =
                Math.max(width, height);

        if (largestDimension <= maxDimension) {
            return source;
        }

        double scale =
                (double) maxDimension
                        / largestDimension;

        int targetWidth =
                Math.max(
                        1,
                        (int) Math.round(width * scale)
                );

        int targetHeight =
                Math.max(
                        1,
                        (int) Math.round(height * scale)
                );

        BufferedImage resized =
                new BufferedImage(
                        targetWidth,
                        targetHeight,
                        BufferedImage.TYPE_INT_RGB
                );

        Graphics2D graphics =
                resized.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            graphics.drawImage(
                    source,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null
            );
        } finally {
            graphics.dispose();
        }

        return resized;
    }

    private byte[] writeJpeg(
            BufferedImage image,
            float quality
    ) {
        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName(
                        "jpeg"
                );

        if (!writers.hasNext()) {
            throw new InvalidImageException(
                    "No hay un encoder JPEG disponible"
            );
        }

        ImageWriter writer =
                writers.next();

        try (
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream();

                ImageOutputStream imageOutput =
                        ImageIO.createImageOutputStream(
                                output
                        )
        ) {
            writer.setOutput(imageOutput);

            ImageWriteParam params =
                    writer.getDefaultWriteParam();

            if (params.canWriteCompressed()) {
                params.setCompressionMode(
                        ImageWriteParam.MODE_EXPLICIT
                );

                params.setCompressionQuality(
                        quality
                );
            }

            writer.write(
                    null,
                    new IIOImage(
                            image,
                            null,
                            null
                    ),
                    params
            );

            imageOutput.flush();

            return output.toByteArray();

        } catch (IOException exception) {
            throw new InvalidImageException(
                    "No se pudo optimizar la imagen JPEG",
                    exception
            );
        } finally {
            writer.dispose();
        }
    }

    private String ensureJpegFilename(
            String filename
    ) {
        if (filename == null || filename.isBlank()) {
            return "cover.jpg";
        }

        String normalized =
                filename.trim();

        int dotIndex =
                normalized.lastIndexOf('.');

        if (dotIndex < 0) {
            return normalized + ".jpg";
        }

        return normalized.substring(
                0,
                dotIndex
        ) + ".jpg";
    }
}