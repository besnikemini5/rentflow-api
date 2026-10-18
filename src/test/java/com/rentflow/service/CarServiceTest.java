package com.rentflow.service;

import com.rentflow.dto.CarResponseDTO;
import com.rentflow.dto.CarSearchDTO;
import com.rentflow.entity.Brand;
import com.rentflow.entity.Car;
import com.rentflow.entity.Location;
import com.rentflow.entity.RentalBooking.BookingStatus;
import com.rentflow.exception.ResourceNotFoundException;
import com.rentflow.repository.BrandRepository;
import com.rentflow.repository.CarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock private CarRepository carRepository;
    @Mock private BrandRepository brandRepository;

    @InjectMocks
    private CarService carService;

    private Car car;

    @BeforeEach
    void setUp() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setName("Toyota");
        brand.setCountry("Japan");

        Location location = new Location();
        location.setId(1L);
        location.setAddress("123 Main Street");
        location.setCity("New York");

        car = new Car();
        car.setId(1L);
        car.setMake("Toyota");
        car.setModel("Camry");
        car.setType("Sedan");
        car.setVin("1HGBH41JXMN109186");
        car.setPricePerDay(new BigDecimal("50.00"));
        car.setStatus(Car.CarStatus.AVAILABLE);
        car.setBrand(brand);
        car.setLocation(location);
    }

    @Test
    void getAllCars_mapsBrandAndLocation() {
        when(carRepository.findAll()).thenReturn(List.of(car));

        List<CarResponseDTO> result = carService.getAllCars();

        assertEquals(1, result.size());
        CarResponseDTO dto = result.get(0);
        assertEquals("Camry", dto.getModel());
        assertEquals("Toyota", dto.getBrandName());
        assertEquals("New York", dto.getLocationCity());
    }

    @Test
    void getCarById_unknownId_throwsNotFound() {
        when(carRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> carService.getCarById(99L));
    }

    @Test
    void getCarsByBrand_unknownBrand_throwsNotFound() {
        when(brandRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> carService.getCarsByBrand(99L));
        verify(carRepository, never()).findByBrandId(any());
    }

    @Test
    void searchCars_withoutDates_usesSimpleSearch() {
        when(carRepository.search(Car.CarStatus.AVAILABLE, 1L, null)).thenReturn(List.of(car));

        List<CarResponseDTO> result = carService.searchCars(new CarSearchDTO(1L, null, null, null));

        assertEquals(1, result.size());
        verify(carRepository, never()).searchAvailableBetween(any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchCars_withDates_excludesBookedCars() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(2);
        when(carRepository.searchAvailableBetween(Car.CarStatus.AVAILABLE, BookingStatus.CANCELLED,
                null, 1L, start, end)).thenReturn(List.of());

        List<CarResponseDTO> result = carService.searchCars(new CarSearchDTO(null, 1L, start, end));

        assertTrue(result.isEmpty());
    }

    @Test
    void searchCars_onlyOneDate_throws() {
        CarSearchDTO search = new CarSearchDTO(null, null, LocalDate.now().plusDays(1), null);

        assertThrows(IllegalArgumentException.class, () -> carService.searchCars(search));
        verifyNoInteractions(carRepository);
    }

    @Test
    void searchCars_startAfterEnd_throws() {
        LocalDate start = LocalDate.now().plusDays(5);
        CarSearchDTO search = new CarSearchDTO(null, null, start, start.minusDays(1));

        assertThrows(IllegalArgumentException.class, () -> carService.searchCars(search));
    }

    @Test
    void updateCarStatus_changesStatus() {
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));

        CarResponseDTO result = carService.updateCarStatus(1L, Car.CarStatus.OUT_OF_SERVICE);

        assertEquals(Car.CarStatus.OUT_OF_SERVICE, result.getStatus());
    }
}
