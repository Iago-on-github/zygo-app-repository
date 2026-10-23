package com.travel_system.backend_app.model.enums;

/*
 * política de cada tipo de imagem aceita no sistema
 * - maxUploadBytes: tamanho máximo do arquivo recebido
 * - maxSidePx: maior lado da imagem final (redimensionada no servidor, mantendo a proporção)
 * */

public enum ImageUploadPolicy {
    PROFILE_PICTURE(150 * 1024, 512),
    REGISTRATION_PHOTO(4 * 1024 * 1024, 800),       // foto 3x4
    REGISTRATION_DOCUMENT(4 * 1024 * 1024, 2000);   // RG/CNH fotografado

    private final long maxUploadBytes;
    private final int maxSidePx;

    ImageUploadPolicy(long maxUploadBytes, int maxSidePx) {
        this.maxUploadBytes = maxUploadBytes;
        this.maxSidePx = maxSidePx;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public int getMaxSidePx() {
        return maxSidePx;
    }
}
