package com.rodrilang.librarymanager.inventory.pricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only public/shared Google Drive folders using the official Drive API. */
@Service
@Slf4j
public class GoogleDrivePriceListService {
    private static final Pattern FOLDER = Pattern.compile("(?:https://)?drive\\.google\\.com/drive/(?:u/\\d+/)?folders/([a-zA-Z0-9_-]+)");
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{10,150}");
    private static final int MAX_FILES = 300;
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper;
    @Value("${anaquel.price-import.drive-api-key:}") private String apiKey;

    public GoogleDrivePriceListService(ObjectMapper mapper) { this.mapper = mapper; }

    public record DriveFile(String id, String name, long size, String modifiedTime) {}
    public record FolderFiles(String folderId, List<DriveFile> files) {}
    public record DownloadedFile(String filename, byte[] content) {}

    public FolderFiles explore(String url) {
        String folderId = folderId(url);
        return new FolderFiles(folderId, list(folderId));
    }

    public DownloadedFile download(String folderUrl, String fileId) {
        String folderId = folderId(folderUrl);
        if (fileId == null || !ID.matcher(fileId).matches()) throw new BusinessException("Identificador de archivo inválido.");
        DriveFile file = list(folderId).stream().filter(item -> item.id().equals(fileId)).findFirst()
                .orElseThrow(() -> new BusinessException("El archivo no pertenece a la carpeta indicada o no está disponible."));
        if (file.size() > MAX_BYTES) throw new BusinessException("La lista supera el máximo de 20 MB para descargas desde Drive.");
        String uri = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media&key=" + enc(apiKey);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.InputStream stream = response.body()) {
                if (response.statusCode() != 200) {
                    byte[] errorBody = stream.readNBytes(16 * 1024);
                    logGoogleError("DOWNLOAD", folderId, fileId, response.statusCode(), errorBody);
                    throw new BusinessException("Google Drive no permitió descargar el archivo (HTTP " + response.statusCode() + ").");
                }
                byte[] contents = stream.readNBytes((int) MAX_BYTES + 1);
                if (contents.length > MAX_BYTES) throw new BusinessException("El archivo descargado supera 20 MB.");
                return new DownloadedFile(file.name(), contents);
            }
        } catch (BusinessException exception) { throw exception; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new BusinessException("Se interrumpió la descarga desde Google Drive."); }
        catch (Exception exception) { throw new BusinessException("No se pudo descargar el archivo de Google Drive."); }
    }

    /** The remote response is checked before our controller starts an octet-stream response. */
    public record OpenDownload(String filename, java.io.InputStream stream) implements AutoCloseable {
        @Override public void close() throws java.io.IOException { stream.close(); }
    }

    public OpenDownload openDownload(String folderUrl, String fileId) {
        String folderId = folderId(folderUrl);
        if (fileId == null || !ID.matcher(fileId).matches()) {
            throw new BusinessException("Identificador de archivo inválido.");
        }
        DriveFile file = list(folderId).stream().filter(f -> f.id().equals(fileId)).findFirst()
                .orElseThrow(() -> new BusinessException("El archivo no pertenece a la carpeta indicada."));
        if (file.size() > MAX_BYTES) throw new BusinessException("El archivo supera el límite de 20 MB.");
        String uri = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media&supportsAllDrives=true&key=" + enc(apiKey);
        try {
            HttpResponse<java.io.InputStream> response = client.send(
                    HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(90)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (var stream = response.body()) {
                    logGoogleError("DOWNLOAD", folderId, fileId, response.statusCode(), stream.readNBytes(16 * 1024));
                } catch (java.io.IOException exception) {
                    log.warn("DRIVE DOWNLOAD error body unavailable folderId={} fileId={} httpStatus={} errorType={}",
                            folderId, fileId, response.statusCode(), exception.getClass().getSimpleName());
                }
                throw new BusinessException("Google Drive no permitió descargar la lista (HTTP "
                        + response.statusCode() + "). Revisá el log DRIVE DOWNLOAD denied.");
            }
            return new OpenDownload(file.name(), response.body());
        } catch (BusinessException exception) { throw exception; }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Se interrumpió la descarga desde Google Drive.");
        } catch (java.io.IOException exception) {
            log.warn("DRIVE DOWNLOAD request failed folderId={} fileId={} type={}", folderId, fileId,
                    exception.getClass().getSimpleName());
            throw new BusinessException("No se pudo iniciar la descarga desde Google Drive.");
        }
    }

    /** Stream one authorized download with a bounded buffer and size check. */
    public void streamDownload(OpenDownload download, java.io.OutputStream output) throws java.io.IOException {
        try (download) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int count;
            while ((count = download.stream().read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new java.io.IOException("El archivo supera el límite de descarga.");
                output.write(buffer, 0, count);
            }
        }
    }

    private List<DriveFile> list(String folderId) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessException("Falta configurar GOOGLE_DRIVE_API_KEY en el backend.");
        List<DriveFile> result = new ArrayList<>();
        String token = null;
        try {
            do {
                String query = "'" + folderId + "' in parents and trashed = false";
                String endpoint = "https://www.googleapis.com/drive/v3/files?q=" + enc(query)
                        + "&fields=" + enc("nextPageToken,incompleteSearch,files(id,name,mimeType,size,modifiedTime,resourceKey)")
                        + "&pageSize=100&supportsAllDrives=true&includeItemsFromAllDrives=true"
                        + "&key=" + enc(apiKey) + (token == null ? "" : "&pageToken=" + enc(token));
                log.info("DRIVE EXPLORE request folderId={} query={} pageSize=100 supportsAllDrives=true includeItemsFromAllDrives=true hasPageToken={}",
                        folderId, query, token != null);
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("DRIVE EXPLORE folderId={} httpStatus={} responseLength={}", folderId, response.statusCode(), response.body().length());
                if (response.statusCode() != 200) {
                    logGoogleError("EXPLORE", folderId, null, response.statusCode(),
                            response.body().substring(0, Math.min(response.body().length(), 16 * 1024)).getBytes(StandardCharsets.UTF_8));
                    throw new BusinessException("No se pudo explorar Drive. Verificá que la carpeta sea pública y que la API esté habilitada (HTTP " + response.statusCode() + ").");
                }
                JsonNode root = mapper.readTree(response.body());
                JsonNode files = root.path("files");
                if (files.isArray() && files.isEmpty() && token == null) {
                    probeFolderMetadata(folderId);
                    log.warn("DRIVE EXPLORE folderId={} Drive files.list devolvió cero elementos. "
                            + "Comprobar credenciales del backend frente al API Explorer (pueden ser diferentes).", folderId);
                }
                int acceptedThisPage = 0;
                log.info("DRIVE EXPLORE folderId={} rawFileCount={} nextPage={} incompleteSearch={}",
                        folderId, files.size(), root.hasNonNull("nextPageToken"), root.path("incompleteSearch").asBoolean(false));
                for (JsonNode file : files) {
                    log.info("DRIVE RAW FILE folderId={} name={} mimeType={} size={}", folderId,
                            file.path("name").asText(""), file.path("mimeType").asText(""), file.path("size").asText(""));
                    String name = file.path("name").asText("");
                    String mime = file.path("mimeType").asText("");
                    if (!(name.toLowerCase(java.util.Locale.ROOT).endsWith(".xls") || name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))) continue;
                    if (!mime.equals("application/vnd.ms-excel") && !mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") && !mime.equals("application/octet-stream")) continue;
                    long size = file.path("size").asLong(0);
                    if (size <= 0 || size > MAX_BYTES) continue;
                    result.add(new DriveFile(file.path("id").asText(), name, size, file.path("modifiedTime").asText("")));
                    acceptedThisPage++;
                    if (result.size() >= MAX_FILES) {
                        log.info("DRIVE EXPLORE folderId={} acceptedTotal={} maxReached=true", folderId, result.size());
                        return result;
                    }
                }
                log.info("DRIVE EXPLORE folderId={} acceptedThisPage={} acceptedTotal={}", folderId, acceptedThisPage, result.size());
                token = root.path("nextPageToken").asText(null);
            } while (token != null && !token.isBlank());
            return result;
        } catch (BusinessException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("Se interrumpió la consulta a Google Drive."); }
        catch (Exception e) { throw new BusinessException("No se pudo consultar la carpeta de Google Drive."); }
    }
    /** Diagnostics only: never logs the API key, raw response body, or a credentialed URL. */
    private void probeFolderMetadata(String folderId) {
        try {
            String endpoint = "https://www.googleapis.com/drive/v3/files/" + folderId
                    + "?fields=" + enc("id,name,mimeType,resourceKey")
                    + "&supportsAllDrives=true&key=" + enc(apiKey);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode metadata = mapper.readTree(response.body());
                log.info("DRIVE FOLDER PROBE folderId={} httpStatus=200 name={} mimeType={} resourceKeyPresent={}",
                        folderId, metadata.path("name").asText(""), metadata.path("mimeType").asText(""),
                        metadata.hasNonNull("resourceKey"));
            } else {
                log.warn("DRIVE FOLDER PROBE folderId={} httpStatus={} (no credentials logged)",
                        folderId, response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("DRIVE FOLDER PROBE folderId={} interrupted", folderId);
        } catch (Exception exception) {
            log.warn("DRIVE FOLDER PROBE folderId={} failed: {}", folderId,
                    exception.getClass().getSimpleName());
        }
    }

    /** Log only the structured error fields returned by Google; never the credentialed request URL or raw response. */
    private void logGoogleError(String operation, String folderId, String fileId, int httpStatus, byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode error = root.path("error");
            String code = error.path("code").asText("unknown");
            String status = error.path("status").asText("unknown");
            String reason = error.path("errors").path(0).path("reason").asText("unknown");
            if ("unknown".equals(reason)) {
                reason = error.path("details").path(0).path("reason").asText("unknown");
            }
            // The message is diagnostic data, not a trusted string: limit size and redact credentials.
            String message = error.path("message").asText("unavailable");
            message = message.replaceAll("(?i)(key|api[_-]?key|access[_-]?token)=([^\\s&]+)", "$1=[REDACTED]")
                    .replaceAll("[\\r\\n\\t]+", " ");
            if (message.length() > 350) message = message.substring(0, 350) + "…";
            log.warn("DRIVE {} denied folderId={} fileId={} httpStatus={} googleCode={} googleStatus={} reason={} message={}",
                    operation, folderId, fileId == null ? "-" : fileId, httpStatus, code, status, reason, message);
        } catch (Exception exception) {
            log.warn("DRIVE {} denied folderId={} fileId={} httpStatus={} errorDetails=unparseable errorType={}",
                    operation, folderId, fileId == null ? "-" : fileId, httpStatus, exception.getClass().getSimpleName());
        }
    }

    private static String folderId(String url) {
        Matcher matcher = FOLDER.matcher(url == null ? "" : url.trim());
        if (!matcher.find()) throw new BusinessException("Ingresá un enlace válido a una carpeta de Google Drive.");
        return matcher.group(1);
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
