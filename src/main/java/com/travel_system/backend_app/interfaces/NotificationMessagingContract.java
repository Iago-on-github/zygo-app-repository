package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.dtos.mensageria.StudentProximityNotificationMessage;
import org.springframework.amqp.core.Message;

public interface NotificationMessagingContract {

    void sendMessage(StudentProximityNotificationMessage event);

    void processFailedMessagesRetryWithParkingLotStrategy(Message failedMessage);
}
