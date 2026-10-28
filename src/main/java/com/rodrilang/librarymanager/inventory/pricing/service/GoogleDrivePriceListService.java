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
                if (response.statusCode() != 200) throw new BusinessException("Google Drive no permitió descargar el archivo (HTTP " + response.statusCode() + ").");
                byte[] contents = stream.readNBytes((int) MAX_BYTES + 1);
                if (contents.length > MAX_BYTES) throw new BusinessException("El archivo descargado supera 20 MB.");
                return new DownloadedFile(file.name(), contents);
            }
        } catch (BusinessException exception) { throw exception; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new BusinessException("Se interrumpió la descarga desde Google Drive."); }
        catch (Exception exception) { throw new BusinessException("No se pudo descargar el archivo de Google Drive."); }
    }

    /** Stream directly from Drive to the HTTP client, bounded to 20 MB. */
    public void streamDownload(String folderUrl, String fileId, java.io.OutputStream output) throws java.io.IOException {
        String folderId = folderId(folderUrl);
        if (fileId == null || !ID.matcher(fileId).matches()) throw new BusinessException("Identificador de archivo inválido.");
        DriveFile file = list(folderId).stream().filter(f -> f.id().equals(fileId)).findFirst()
                .orElseThrow(() -> new BusinessException("El archivo no pertenece a la carpeta indicada."));
        if (file.size() > MAX_BYTES) throw new BusinessException("El archivo supera el límite de 20 MB.");
        String uri = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media&key=" + enc(apiKey);
        try {
            HttpResponse<java.io.InputStream> response = client.send(
                    HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(90)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) throw new BusinessException("Google Drive rechazó la descarga (HTTP " + response.statusCode() + ").");
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw new BusinessException("El archivo supera el límite de descarga.");
                    output.write(buffer, 0, count);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("Descarga interrumpida", e);
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
                    log.warn("DRIVE EXPLORE folderId={} errorHttp={} (sin registrar URL ni credenciales)", folderId, response.statusCode());
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

    private static String folderId(String url) {
        Matcher matcher = FOLDER.matcher(url == null ? "" : url.trim());
        if (!matcher.find()) throw new BusinessException("Ingresá un enlace válido a una carpeta de Google Drive.");
        return matcher.group(1);
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
