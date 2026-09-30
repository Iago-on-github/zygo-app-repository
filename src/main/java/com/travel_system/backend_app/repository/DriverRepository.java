package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    @Query("SELECT d FROM Driver d WHERE d.userAccount.email = :email")
    Optional<Driver> findByEmail(String email);

    Page<Driver> findAllByStatus(GeneralStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE Driver d SET d.totalTrips = :newValueOfTotalTrips")
    void updateTotalTrips(@Param("newValueOfTotalTrips") int newValueOfTotalTrips);

    Optional<Driver> findByUserAccountId(@Param("userAccountId") UUID userAccountId);

    boolean existsByTelephone(@Param("telephone") String telephone);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM driver_table WHERE telephone = :telephone)", nativeQuery = true)
    boolean existsByTelephoneIgnoringTenant(@Param("telephone") String telephone);

    @Query("SELECT COUNT(s) > 0 FROM Driver adm WHERE adm.customerId = :customerId")
    int countDriverInThisCustomer(UUID customerId);
}
