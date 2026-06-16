package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.StudentAwayStateDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentTravelRepository extends JpaRepository<StudentTravel, UUID> {

    @Query("""
            SELECT new com.travel_system.backend_app.model.dtos.StudentAwayStateDTO(
                st.id,
                st.student.id,
                st.student.email,
                st.studentTravelStatus,
                st.embark
            )
            FROM StudentTravel st
            WHERE st.travel.id = :travelId
        """)
    List<StudentAwayStateDTO> findStudentsForAwayState(@Param("travelId") UUID travelId);

    @Modifying
    @Query("UPDATE StudentTravel st SET st.studentTravelStatus = :status WHERE st.id IN :studentTravelId")
    void updateStudentTravelStatus(@Param("studentTravelId") List<UUID> studentTravelId, @Param("status") StudentTravelStatus status);

    @Modifying
    @Query("UPDATE StudentTravel st SET st.studentTravelStatus = :status, st.disembarkHour = :disembarkHour, st.embark = :embark WHERE st.id IN :studentTravelIds")
    void disconnectedStudentFromTrip(@Param("studentTravelIds") List<UUID> studentTravelIds, @Param("status") StudentTravelStatus status, @Param("disembarkHour") Instant disembarkHour, @Param("embark") boolean embark);

    Optional<StudentTravel> findByStudentIdAndTravelId(UUID studentId, UUID travelId);

    Optional<StudentTravel> findByTravelIdAndStudentId(UUID id, UUID studentId);

    @Query(value = "SELECT st.student.id from StudentTravel st WHERE st.travel.id = :travelId AND st.disembarkHour IS NULL ")
    List<UUID> findStudentIdsByTravelIdAndDisembarkHourIsNull(UUID travelId);

    boolean existsByIdAndTravelId(UUID studentId, UUID travelId);

    @Query("SELECT st FROM StudentTravel st WHERE st.travel.id = :travelId AND st.student.email = :studentEmail")
    Optional<StudentTravel> findByTravelIdAndStudentEmail(@Param("travelId") UUID travelId, @Param("studentEmail") String studentEmail);
}
