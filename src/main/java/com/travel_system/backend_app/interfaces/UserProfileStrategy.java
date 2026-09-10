package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;

public interface UserProfileStrategy {

    boolean supports(UserAccountType userAccountType);

    void updatePicture(UserAccount userAccount, String pictureKey);
    void deletePicture(UserAccount userAccount);
}


/*
* GUIDE
*
* implementa Strategy para identificar cada usuario de forma específica (Student, Driver, etc...)
* retira a necessidade de um Switch com todo o mapeamento dos usuários
* */
