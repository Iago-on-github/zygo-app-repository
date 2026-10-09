package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.CustomerHoliday;
import com.travel_system.backend_app.model.enums.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CustomerHolidayRepository extends JpaRepository<CustomerHoliday, UUID> {

    boolean existsByDate(LocalDate date);

    // feriado sem turnos vale para o dia inteiro, então entra em qualquer filtro de turno
    @Query("""
    SELECT h FROM CustomerHoliday h
    WHERE (CAST(:from AS LocalDate) IS NULL OR h.date >= :from)
      AND (CAST(:to AS LocalDate) IS NULL OR h.date <= :to)
      AND (:filterByShifts = false
           OR h.shifts IS EMPTY
           OR EXISTS (SELECT 1 FROM CustomerHoliday h2 JOIN h2.shifts s WHERE h2 = h AND s IN :shifts))
    """)
    Page<CustomerHoliday> findAllByPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("filterByShifts") boolean filterByShifts, @Param("shifts") Set<Shift> shifts, Pageable pageable);

    List<CustomerHoliday> findAllByDateBetweenOrderByDateAsc(LocalDate today, LocalDate localDate);
}
