package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.SensitiveDataAccessLog;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.dtos.response.SensitiveDataAccessLogResponseDTO;
import com.travel_system.backend_app.model.enums.Action;
import com.travel_system.backend_app.model.enums.ResourceType;
import com.travel_system.backend_app.repository.SensitiveDataAccessLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SensitiveDataAccessLogService {

    private final SensitiveDataAccessLogRepository sensitiveDataAccessLogRepository;

    public SensitiveDataAccessLogService(SensitiveDataAccessLogRepository sensitiveDataAccessLogRepository) {
        this.sensitiveDataAccessLogRepository = sensitiveDataAccessLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<SensitiveDataAccessLogResponseDTO> getAllSensitivesAccessLog(UUID ownerId, UUID actorAccountId, Instant from, Instant to, Pageable pageable) {
        return sensitiveDataAccessLogRepository.search(ownerId, actorAccountId, from, to, pageable).map(dataAccessLog ->
                new SensitiveDataAccessLogResponseDTO(
                        dataAccessLog.getId(),
                        dataAccessLog.getActorAccountId(),
                        dataAccessLog.getOwnerType(),
                        dataAccessLog.getOwnerId(),
                        dataAccessLog.getResourceType(),
                        dataAccessLog.getResourceId(),
                        dataAccessLog.getAction(),
                        dataAccessLog.getContextId(),
                        dataAccessLog.getAccessAt()
                ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorAccountId, OwnerBuilt owner, ResourceType resourceType, UUID resourceId, Action action, UUID contextId) {
        SensitiveDataAccessLog log = new SensitiveDataAccessLog();

        if (TenantContext.getCurrentTenant() == null) {
            throw new CustomerNotFoundException("Customer não encontrado");
        }

        log.assignCustomer(TenantContext.getCurrentTenant());
        log.setActorAccountId(actorAccountId);
        log.setOwnerType(owner.type());
        log.setOwnerId(owner.id());
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setAction(action);
        log.setContextId(contextId);
        log.setAccessAt(Instant.now());

        sensitiveDataAccessLogRepository.save(log);
    }
}

