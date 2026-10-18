package com.rentflow.controller;

import com.rentflow.config.OpenApiConfig;
import com.rentflow.dto.CarResponseDTO;
import com.rentflow.dto.CarSearchDTO;
import com.rentflow.entity.Car;
import com.rentflow.service.CarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Tag(name = "Car", description = "Browse and search cars")
public class CarController {

    private final CarService carService;

    @GetMapping
    @Operation(summary = "List all cars")
    public List<CarResponseDTO> getAllCars() {
        return carService.getAllCars();
    }

    @GetMapping("/available")
    @Operation(summary = "List cars that are in service")
    public List<CarResponseDTO> getAvailableCars() {
        return carService.getAvailableCars();
    }

    @GetMapping("/search")
    @Operation(summary = "Search cars",
            description = "Optional filters: brandId, locationId, and a date range (startDate and endDate, yyyy-MM-dd, inclusive). "
                    + "With dates, cars already booked on any of those days are excluded.")
    public List<CarResponseDTO> searchCars(@ParameterObject @ModelAttribute CarSearchDTO search) {
        return carService.searchCars(search);
    }

    @GetMapping("/brand/{brandId}")
    @Operation(summary = "List cars of a brand")
    public List<CarResponseDTO> getCarsByBrand(@PathVariable Long brandId) {
        return carService.getCarsByBrand(brandId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a car")
    public CarResponseDTO getCarById(@PathVariable Long id) {
        return carService.getCarById(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BASIC_AUTH)
    @Operation(summary = "Change a car's status (admin)", description = "AVAILABLE or OUT_OF_SERVICE")
    public CarResponseDTO updateCarStatus(@PathVariable Long id, @RequestParam Car.CarStatus status) {
        return carService.updateCarStatus(id, status);
    }
}
