package com.rentflow.service;

import com.rentflow.dto.CarResponseDTO;
import com.rentflow.dto.CarSearchDTO;
import com.rentflow.entity.Car;
import com.rentflow.entity.RentalBooking.BookingStatus;
import com.rentflow.exception.ResourceNotFoundException;
import com.rentflow.repository.BrandRepository;
import com.rentflow.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarService {

    private final CarRepository carRepository;
    private final BrandRepository brandRepository;

    public List<CarResponseDTO> getAllCars() {
        return toResponses(carRepository.findAll());
    }

    public CarResponseDTO getCarById(Long id) {
        return toResponse(findCar(id));
    }

    public List<CarResponseDTO> getAvailableCars() {
        return toResponses(carRepository.findByStatus(Car.CarStatus.AVAILABLE));
    }

    public List<CarResponseDTO> getCarsByBrand(Long brandId) {
        if (!brandRepository.existsById(brandId)) {
            throw new ResourceNotFoundException("Brand not found with id: " + brandId);
        }
        return toResponses(carRepository.findByBrandId(brandId));
    }

    /**
     * Finds rentable cars. With a date range, cars already booked on any of
     * those days are left out; without one, all rentable cars are returned.
     */
    public List<CarResponseDTO> searchCars(CarSearchDTO search) {
        LocalDate start = search.getStartDate();
        LocalDate end = search.getEndDate();

        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("Provide both startDate and endDate, or neither");
        }

        List<Car> cars;
        if (start == null) {
            cars = carRepository.search(Car.CarStatus.AVAILABLE, search.getBrandId(), search.getLocationId());
        } else {
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Start date must be on or before end date");
            }
            cars = carRepository.searchAvailableBetween(Car.CarStatus.AVAILABLE, BookingStatus.CANCELLED,
                    search.getBrandId(), search.getLocationId(), start, end);
        }
        return toResponses(cars);
    }

    /** Admin only (enforced in the controller). */
    @Transactional
    public CarResponseDTO updateCarStatus(Long carId, Car.CarStatus status) {
        Car car = findCar(carId);
        car.setStatus(status);
        return toResponse(car);
    }

    private Car findCar(Long id) {
        return carRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with id: " + id));
    }

    private List<CarResponseDTO> toResponses(List<Car> cars) {
        return cars.stream().map(this::toResponse).toList();
    }

    private CarResponseDTO toResponse(Car car) {
        CarResponseDTO dto = new CarResponseDTO();
        dto.setId(car.getId());
        dto.setMake(car.getMake());
        dto.setModel(car.getModel());
        dto.setPricePerDay(car.getPricePerDay());
        dto.setVin(car.getVin());
        dto.setStatus(car.getStatus());
        dto.setType(car.getType());

        dto.setBrandId(car.getBrand().getId());
        dto.setBrandName(car.getBrand().getName());
        dto.setBrandCountry(car.getBrand().getCountry());

        dto.setLocationId(car.getLocation().getId());
        dto.setLocationAddress(car.getLocation().getAddress());
        dto.setLocationCity(car.getLocation().getCity());
        return dto;
    }
}
