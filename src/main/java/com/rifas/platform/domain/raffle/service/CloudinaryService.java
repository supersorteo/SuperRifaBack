package com.rifas.platform.domain.raffle.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rifas.platform.config.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService implements ImageStorageService {

    private final Cloudinary cloudinary;
    private final CloudinaryProperties props;

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    @Override
    @SuppressWarnings("unchecked")
    public UploadResult upload(MultipartFile file, UUID raffleId) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen no puede superar 2 MB");
        }

        String publicId = props.getUploadFolder() + "/raffles/" + raffleId + "/" + UUID.randomUUID();

        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id",     publicId,
                "overwrite",     true,
                "resource_type", "image",
                "format",        "webp",
                "transformation", ObjectUtils.asMap(
                        "width",   1200,
                        "height",  800,
                        "crop",    "limit",
                        "quality", "auto:good"
                )
        ));

        return new UploadResult(
                (String) result.get("public_id"),
                (String) result.get("secure_url")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public UploadResult uploadAvatar(MultipartFile file, UUID organizerId) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen no puede superar 2 MB");
        }
        String publicId = props.getUploadFolder() + "/avatars/" + organizerId;
        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id",     publicId,
                "overwrite",     true,
                "resource_type", "image",
                "format",        "webp",
                "transformation", ObjectUtils.asMap(
                        "width",   400,
                        "height",  400,
                        "crop",    "fill",
                        "gravity", "face",
                        "quality", "auto:good"
                )
        ));
        return new UploadResult(publicId, (String) result.get("secure_url"));
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.warn("No se pudo eliminar imagen de Cloudinary: {}", publicId);
        }
    }
}
