package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.interfaces.StorageInterfaceService;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class StorageUrlMapper {

    private final StorageInterfaceService storageInterfaceService;

    public StorageUrlMapper(StorageInterfaceService storageInterfaceService) {
        this.storageInterfaceService = storageInterfaceService;
    }

    @Named("toPublicUrl")
    public String getPublicUrl(String objectKey) {
        return storageInterfaceService.getPublicUrl(objectKey);
    }
}
