package com.rentflow.exception;

/** Thrown when the car is already booked for the requested dates (HTTP 409). */
public class BookingOverlapException extends RuntimeException {

    public BookingOverlapException(String message) {
        super(message);
    }
}
