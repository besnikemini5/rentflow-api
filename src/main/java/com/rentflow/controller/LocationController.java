package com.rentflow.controller;

import com.rentflow.dto.LocationResponseDTO;
import com.rentflow.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Location", description = "Pickup and dropoff locations")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @Operation(summary = "List all locations")
    public List<LocationResponseDTO> getAllLocations() {
        return locationService.getAllLocations();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a location")
    public LocationResponseDTO getLocationById(@PathVariable Long id) {
        return locationService.getLocationById(id);
    }

    @GetMapping("/city/{city}")
    @Operation(summary = "List locations in a city (case-insensitive)")
    public List<LocationResponseDTO> getLocationsByCity(@PathVariable String city) {
        return locationService.getLocationsByCity(city);
    }
}
