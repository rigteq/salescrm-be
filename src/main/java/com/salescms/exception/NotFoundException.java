package com.salescms.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String objectType, Object id) {
        super(objectType + " not found: " + id);
    }
}
