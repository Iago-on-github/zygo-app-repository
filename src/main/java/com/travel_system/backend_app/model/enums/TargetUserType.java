package com.travel_system.backend_app.model.enums;

public enum TargetUserType {
    STUDENT (UserAccountType.STUDENT),
    DRIVER (UserAccountType.DRIVER),
    ADMINISTRATOR (UserAccountType.ADMINISTRATOR),
    PLATFORM_ADMINISTRATOR (UserAccountType.PLATFORM_ADMINISTRATOR),
    RESPONSIBLE_ADULT (UserAccountType.RESPONSIBLE_ADULT);

    private final UserAccountType userAccountType;

    TargetUserType(UserAccountType userAccountType) {
        this.userAccountType = userAccountType;
    }

    public UserAccountType getUserAccountType() {
        return userAccountType;
    }
}
