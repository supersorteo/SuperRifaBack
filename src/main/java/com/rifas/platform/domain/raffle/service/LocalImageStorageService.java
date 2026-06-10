package com.rifas.platform.domain.raffle.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("!prod")
@Slf4j
public class LocalImageStorageService implements ImageStorageService {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    // Extension derived from content-type, never from user-supplied filename
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp"
    );

    @Value("${app.upload-path:${user.home}/rifas-uploads}")
    private String uploadPath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public UploadResult upload(MultipartFile file, UUID raffleId) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen no puede superar 2 MB");
        }

        // (a) validate content-type against allowlist — reject SVG, HTML, etc.
        String contentType = file.getContentType();
        String ext = ALLOWED_TYPES.get(contentType);
        if (ext == null) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido. Solo JPG, PNG o WebP.");
        }

        Path dir = Paths.get(uploadPath, "raffles", raffleId.toString())
                        .normalize().toAbsolutePath();
        Files.createDirectories(dir);

        // (b) filename built entirely from UUID + allowlisted extension — no user input
        String filename = UUID.randomUUID() + ext;
        Path dest = dir.resolve(filename);

        // (c) path traversal guard: dest must stay inside dir
        if (!dest.normalize().toAbsolutePath().startsWith(dir)) {
            throw new SecurityException("Ruta de destino inválida");
        }

        file.transferTo(dest);

        String publicId = "raffles/" + raffleId + "/" + filename;
        String url = baseUrl + "/uploads/" + publicId;

        log.info("Imagen guardada localmente: {}", dest);
        return new UploadResult(publicId, url);
    }

    @Override
    public void delete(String publicId) {
        try {
            // Guard: publicId must not escape uploadPath
            Path base = Paths.get(uploadPath).normalize().toAbsolutePath();
            Path target = base.resolve(publicId).normalize().toAbsolutePath();
            if (!target.startsWith(base)) {
                log.warn("Intento de path traversal en delete: {}", publicId);
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("No se pudo eliminar imagen local: {}", publicId);
        }
    }
}
