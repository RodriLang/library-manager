package com.rodrilang.librarymanager.inventory.pricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
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

    private List<DriveFile> list(String folderId) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessException("Falta configurar GOOGLE_DRIVE_API_KEY en el backend.");
        List<DriveFile> result = new ArrayList<>();
        String token = null;
        try {
            do {
                String query = "'" + folderId + "' in parents and trashed = false";
                String endpoint = "https://www.googleapis.com/drive/v3/files?q=" + enc(query)
                        + "&fields=" + enc("nextPageToken,files(id,name,mimeType,size,modifiedTime)")
                        + "&pageSize=100&key=" + enc(apiKey) + (token == null ? "" : "&pageToken=" + enc(token));
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new BusinessException("No se pudo explorar Drive. Verificá que la carpeta sea pública y que la API esté habilitada (HTTP " + response.statusCode() + ").");
                JsonNode root = mapper.readTree(response.body());
                for (JsonNode file : root.path("files")) {
                    String name = file.path("name").asText("");
                    String mime = file.path("mimeType").asText("");
                    if (!(name.toLowerCase(java.util.Locale.ROOT).endsWith(".xls") || name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))) continue;
                    if (!mime.equals("application/vnd.ms-excel") && !mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") && !mime.equals("application/octet-stream")) continue;
                    long size = file.path("size").asLong(0);
                    if (size <= 0 || size > MAX_BYTES) continue;
                    result.add(new DriveFile(file.path("id").asText(), name, size, file.path("modifiedTime").asText("")));
                    if (result.size() >= MAX_FILES) return result;
                }
                token = root.path("nextPageToken").asText(null);
            } while (token != null && !token.isBlank());
            return result;
        } catch (BusinessException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("Se interrumpió la consulta a Google Drive."); }
        catch (Exception e) { throw new BusinessException("No se pudo consultar la carpeta de Google Drive."); }
    }
    private static String folderId(String url) {
        Matcher matcher = FOLDER.matcher(url == null ? "" : url.trim());
        if (!matcher.find()) throw new BusinessException("Ingresá un enlace válido a una carpeta de Google Drive.");
        return matcher.group(1);
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
