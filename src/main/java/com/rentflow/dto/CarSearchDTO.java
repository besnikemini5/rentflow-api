package com.rentflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Optional filters for GET /api/cars/search. All fields may be omitted. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarSearchDTO {

    private Long brandId;
    private Long locationId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
