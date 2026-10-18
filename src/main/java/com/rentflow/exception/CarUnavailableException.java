package com.rentflow.exception;

/** Thrown when the car is out of service and cannot be booked (HTTP 409). */
public class CarUnavailableException extends RuntimeException {

    public CarUnavailableException(String message) {
        super(message);
    }
}
