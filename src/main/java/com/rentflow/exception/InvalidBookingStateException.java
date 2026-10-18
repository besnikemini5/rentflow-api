package com.rentflow.exception;

/** Thrown when a booking status change is not allowed, e.g. confirming a cancelled booking (HTTP 409). */
public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
