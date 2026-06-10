package com.rifas.platform.domain.raffle.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface ImageStorageService {

    UploadResult upload(MultipartFile file, UUID raffleId) throws IOException;

    void delete(String publicId);

    record UploadResult(String publicId, String url) {}
}
