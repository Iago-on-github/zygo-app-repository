package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.CustomerTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerTermRepository extends JpaRepository<CustomerTerm, UUID> {

    Optional<CustomerTerm> findFirstByStartTermLessThanEqualAndEndTermGreaterThanEqual(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<CustomerTerm> findAllByOrderByStartTermDesc();

    // dois intervalos se sobrepõem quando um começa antes de o outro terminar, e vice-versa
    @Query("SELECT COUNT(t) > 0 FROM CustomerTerm t WHERE t.startTerm <= :end AND t.endTerm >= :start")
    boolean existsOverlapping(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(t) > 0 FROM CustomerTerm t WHERE t.startTerm <= :end AND t.endTerm >= :start AND t.id <> :ignoreId")
    boolean existsOverlappingExcluding(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("ignoreId") UUID ignoreId);
}
