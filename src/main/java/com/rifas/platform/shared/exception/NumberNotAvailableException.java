package com.rifas.platform.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NumberNotAvailableException extends RuntimeException {
    public NumberNotAvailableException(Integer number) {
        super("El número " + number + " ya no está disponible");
    }
}
