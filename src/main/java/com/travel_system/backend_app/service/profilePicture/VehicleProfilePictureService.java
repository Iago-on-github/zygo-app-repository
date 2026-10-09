package com.travel_system.backend_app.service.profilePicture;

import com.travel_system.backend_app.exceptions.VehicleNotFoundException;
import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.repository.VehicleRepository;
import com.travel_system.backend_app.service.ImageStorageService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class VehicleProfilePictureService {

    private final VehicleRepository vehicleRepository;
    private final ImageStorageService imageStorageService;

    public VehicleProfilePictureService(VehicleRepository vehicleRepository, ImageStorageService imageStorageService) {
        this.vehicleRepository = vehicleRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public void updateVehiclePicture(UUID vehicleId, MultipartFile file) throws IOException {
        Vehicle vehicle = findVehicle(vehicleId);

        String key = "customers/" + vehicle.getCustomerId() + "/vehicles/" + vehicle.getId() + "/picture.jpeg";
        vehicle.setVehicleImage(imageStorageService.store(file, key));
    }

    @Transactional
    public void deleteVehiclePicture(UUID vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);

        String currentKey = vehicle.getVehicleImage();
        if (currentKey == null) {
            return;
        }

        vehicle.setVehicleImage(null);
        imageStorageService.delete(currentKey);
    }

    // findById passa pelo filtro de tenant (applyToLoadByKey): só encontra veículos do Customer do admin
    private Vehicle findVehicle(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Veículo não encontrado"));
    }
}
