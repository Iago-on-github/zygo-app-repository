package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.model.dtos.response.VehicleResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.VehicleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Modifying
    @Query("UPDATE Vehicle v SET v.totalTrips = :newValueOfVehicleTotalTrips")
    void updateTotalTrips(int newValueOfVehicleTotalTrips);

    @Query("""
    SELECT v FROM Vehicle v
    WHERE v.customerId = :customerId
      AND (:vehicleType IS NULL OR v.vehicleType = :vehicleType)
      AND (:vehicleNumber IS NULL OR v.vehicleNumber = :vehicleNumber)
      AND (:numberPlate IS NULL OR UPPER(REPLACE(v.numberPlate, '-', '')) = :numberPlate)
      AND (:status IS NULL OR v.status = :status)
      AND (:color IS NULL OR LOWER(v.color) = :color)
""")
    Page<Vehicle> findAllByOptionalFilters(
            @Param("customerId") UUID customerId,
            @Param("vehicleType") VehicleType vehicleType,
            @Param("vehicleNumber") String vehicleNumber,
            @Param("numberPlate") String numberPlate,
            @Param("status") GeneralStatus status,
            @Param("color") String color,
            Pageable pageable);
    boolean existsByVehicleNumber(@Param("vehicleNumber") String vehicleNumber);

    boolean existsByNumberPlate(@Param("numberPlate") String numberPlate);
}
