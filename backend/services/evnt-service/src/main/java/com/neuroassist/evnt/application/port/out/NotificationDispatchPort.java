package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.NotificationDispatch;

public interface NotificationDispatchPort {

    void dispatch(NotificationDispatch dispatch);
}
