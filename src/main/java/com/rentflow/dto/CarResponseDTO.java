package com.rentflow.dto;

import com.rentflow.entity.Car.CarStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CarResponseDTO {

    private Long id;
    private String make;
    private String model;
    private BigDecimal pricePerDay;
    private String vin;
    private CarStatus status;
    private String type;

    private Long brandId;
    private String brandName;
    private String brandCountry;

    private Long locationId;
    private String locationAddress;
    private String locationCity;
}
