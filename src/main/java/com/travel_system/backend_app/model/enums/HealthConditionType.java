package com.travel_system.backend_app.model.enums;

import java.util.EnumSet;

import static com.travel_system.backend_app.model.enums.DocumentType.*;

public enum HealthConditionType {

    ALLERGY(true, EnumSet.of(EMERGENCY_ACTION_PLAN, MEDICAL_REPORT, PRESCRIPTION)),
    DIETARY_RESTRICTION(false, EnumSet.of(MEDICAL_REPORT)),
    PHYSICAL_CONDITION(true, EnumSet.of(MEDICAL_REPORT)),
    NEURODIVERGENCE(true, EnumSet.of(MEDICAL_REPORT)),
    CHRONIC_ILLNESS(true, EnumSet.of(EMERGENCY_ACTION_PLAN, MEDICAL_REPORT, PRESCRIPTION)),
    CONTINUOUS_MEDICATION(true, EnumSet.of(EMERGENCY_ACTION_PLAN, MEDICAL_REPORT));

    private final boolean requiresMedicalReport;
    private final EnumSet<DocumentType> documentType;

    HealthConditionType(boolean requiresMedicalReport, EnumSet<DocumentType> documentType) {
        this.requiresMedicalReport = requiresMedicalReport;
        this.documentType = documentType;
    }

    public boolean isRequiresMedicalReport() {
        return requiresMedicalReport;
    }

    public EnumSet<DocumentType> getDocumentType() {
        return documentType;
    }
}

/*
* GUIDE
* exibe condições médicas comuns e se precisa de comprovação médica
* */
