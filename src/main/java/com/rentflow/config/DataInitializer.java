package com.rentflow.config;

import com.rentflow.entity.Brand;
import com.rentflow.entity.Car;
import com.rentflow.entity.Location;
import com.rentflow.entity.User;
import com.rentflow.repository.BrandRepository;
import com.rentflow.repository.CarRepository;
import com.rentflow.repository.LocationRepository;
import com.rentflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds demo data on startup (the database is in-memory).
 * The demo credentials are intentionally public and documented in the README.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final BrandRepository brandRepository;
    private final CarRepository carRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (carRepository.count() == 0) {
            Brand toyota = brandRepository.save(brand("Toyota", "Japan"));
            Brand bmw = brandRepository.save(brand("BMW", "Germany"));

            Location newYork = locationRepository.save(location("123 Main Street", "New York"));
            Location losAngeles = locationRepository.save(location("456 Oak Avenue", "Los Angeles"));

            carRepository.save(car(toyota, newYork, "Toyota", "Camry", "Sedan", "50.00", "1HGBH41JXMN109186"));
            carRepository.save(car(toyota, newYork, "Toyota", "RAV4", "SUV", "65.00", "2HGBH41JXMN109187"));
            carRepository.save(car(bmw, losAngeles, "BMW", "320i", "Sedan", "80.00", "3HGBH41JXMN109188"));
            carRepository.save(car(bmw, losAngeles, "BMW", "X5", "SUV", "120.00", "4HGBH41JXMN109189"));
        }

        if (userRepository.count() == 0) {
            userRepository.save(user("admin", "admin123", "admin@rentflow.com", User.Role.ADMIN));
            userRepository.save(user("user", "user123", "user@rentflow.com", User.Role.CUSTOMER));
        }
    }

    private Brand brand(String name, String country) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setCountry(country);
        return brand;
    }

    private Location location(String address, String city) {
        Location location = new Location();
        location.setAddress(address);
        location.setCity(city);
        return location;
    }

    private Car car(Brand brand, Location location, String make, String model, String type,
                    String pricePerDay, String vin) {
        Car car = new Car();
        car.setBrand(brand);
        car.setLocation(location);
        car.setMake(make);
        car.setModel(model);
        car.setType(type);
        car.setPricePerDay(new BigDecimal(pricePerDay));
        car.setVin(vin);
        car.setStatus(Car.CarStatus.AVAILABLE);
        return car;
    }

    private User user(String username, String rawPassword, String email, User.Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
