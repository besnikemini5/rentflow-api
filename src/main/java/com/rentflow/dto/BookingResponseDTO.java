package com.rentflow.dto;

import com.rentflow.entity.RentalBooking.BookingStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class BookingResponseDTO {

    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private BookingStatus status;
    private BigDecimal totalCost;
    private Long durationInDays;

    private String customerUsername;
    private String customerEmail;

    private Long carId;
    private String carMake;
    private String carModel;
    private String carType;

    private Long pickupLocationId;
    private String pickupAddress;
    private String pickupCity;

    private Long dropoffLocationId;
    private String dropoffAddress;
    private String dropoffCity;

    /** Null until the booking is confirmed. */
    private Long invoiceId;
}
