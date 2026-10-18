package com.rentflow.service;

import com.rentflow.dto.LocationResponseDTO;
import com.rentflow.entity.Location;
import com.rentflow.exception.ResourceNotFoundException;
import com.rentflow.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;

    public List<LocationResponseDTO> getAllLocations() {
        return locationRepository.findAll().stream().map(this::toResponse).toList();
    }

    public LocationResponseDTO getLocationById(Long id) {
        return locationRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
    }

    public List<LocationResponseDTO> getLocationsByCity(String city) {
        return locationRepository.findByCityIgnoreCase(city).stream().map(this::toResponse).toList();
    }

    private LocationResponseDTO toResponse(Location location) {
        return new LocationResponseDTO(location.getId(), location.getAddress(), location.getCity());
    }
}
