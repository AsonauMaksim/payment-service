package com.internship.payment_service.exception;

public class RandomApiUnavailableException extends RuntimeException{

    public RandomApiUnavailableException(String message) {
        super(message);
    }

    public RandomApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
