package com.rentflow.service;

import com.rentflow.dto.BrandResponseDTO;
import com.rentflow.entity.Brand;
import com.rentflow.exception.ResourceNotFoundException;
import com.rentflow.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    public List<BrandResponseDTO> getAllBrands() {
        return brandRepository.findAll().stream().map(this::toResponse).toList();
    }

    public BrandResponseDTO getBrandById(Long id) {
        return brandRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
    }

    private BrandResponseDTO toResponse(Brand brand) {
        return new BrandResponseDTO(brand.getId(), brand.getName(), brand.getCountry());
    }
}
