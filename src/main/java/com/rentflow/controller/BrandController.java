package com.rentflow.controller;

import com.rentflow.dto.BrandResponseDTO;
import com.rentflow.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@Tag(name = "Brand", description = "Car brands")
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @Operation(summary = "List all brands")
    public List<BrandResponseDTO> getAllBrands() {
        return brandService.getAllBrands();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a brand")
    public BrandResponseDTO getBrandById(@PathVariable Long id) {
        return brandService.getBrandById(id);
    }
}
