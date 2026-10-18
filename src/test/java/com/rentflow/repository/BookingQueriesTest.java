package com.rentflow.repository;

import com.rentflow.entity.Brand;
import com.rentflow.entity.Car;
import com.rentflow.entity.Location;
import com.rentflow.entity.RentalBooking;
import com.rentflow.entity.RentalBooking.BookingStatus;
import com.rentflow.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real JPQL queries against an in-memory database.
 * Fixture: car A has a PENDING booking from Jan 10 to Jan 15 (both days inclusive).
 */
@DataJpaTest
class BookingQueriesTest {

    private static final LocalDate JAN_10 = LocalDate.of(2030, 1, 10);
    private static final LocalDate JAN_12 = LocalDate.of(2030, 1, 12);
    private static final LocalDate JAN_15 = LocalDate.of(2030, 1, 15);

    @Autowired private TestEntityManager em;
    @Autowired private RentalBookingRepository bookingRepository;
    @Autowired private CarRepository carRepository;

    private Car carA;
    private Car carB;
    private Location newYork;
    private Location losAngeles;
    private RentalBooking existing;

    @BeforeEach
    void setUp() {
        Brand brand = new Brand();
        brand.setName("Toyota");
        brand.setCountry("Japan");
        em.persist(brand);

        newYork = location("New York");
        losAngeles = location("Los Angeles");

        User user = new User();
        user.setUsername("customer");
        user.setPassword("hash");
        user.setEmail("customer@example.com");
        user.setRole(User.Role.CUSTOMER);
        em.persist(user);

        carA = car(brand, newYork, "VIN-A");
        carB = car(brand, newYork, "VIN-B");

        existing = new RentalBooking();
        existing.setCar(carA);
        existing.setUser(user);
        existing.setPickupLocation(newYork);
        existing.setDropoffLocation(newYork);
        existing.setStartDate(JAN_10);
        existing.setEndDate(JAN_15);
        existing.setStatus(BookingStatus.PENDING);
        existing.setTotalCost(new BigDecimal("300.00"));
        em.persist(existing);
        em.flush();
    }

    // --- overlap detection ---

    @Test
    void sameRange_overlaps() {
        assertThat(overlaps(carA, JAN_10, JAN_15)).isTrue();
    }

    @Test
    void startingOnTheLastBookedDay_overlaps() {
        assertThat(overlaps(carA, JAN_15, JAN_15.plusDays(3))).isTrue();
    }

    @Test
    void endingOnTheFirstBookedDay_overlaps() {
        assertThat(overlaps(carA, JAN_10.minusDays(3), JAN_10)).isTrue();
    }

    @Test
    void rangeInsideTheBooking_overlaps() {
        assertThat(overlaps(carA, JAN_10.plusDays(1), JAN_15.minusDays(1))).isTrue();
    }

    @Test
    void rangeCoveringTheBooking_overlaps() {
        assertThat(overlaps(carA, JAN_10.minusDays(5), JAN_15.plusDays(5))).isTrue();
    }

    @Test
    void startingTheDayAfter_doesNotOverlap() {
        assertThat(overlaps(carA, JAN_15.plusDays(1), JAN_15.plusDays(4))).isFalse();
    }

    @Test
    void endingTheDayBefore_doesNotOverlap() {
        assertThat(overlaps(carA, JAN_10.minusDays(4), JAN_10.minusDays(1))).isFalse();
    }

    @Test
    void cancelledBookings_areIgnored() {
        existing.setStatus(BookingStatus.CANCELLED);
        em.flush();

        assertThat(overlaps(carA, JAN_10, JAN_15)).isFalse();
    }

    @Test
    void bookingsOfOtherCars_areIgnored() {
        assertThat(overlaps(carB, JAN_10, JAN_15)).isFalse();
    }

    // --- car search ---

    @Test
    void searchWithDates_excludesCarBookedOnThoseDays() {
        assertThat(searchIds(null, JAN_12, JAN_12)).containsExactly(carB.getId());
    }

    @Test
    void searchWithDates_includesCarOnOtherDays() {
        assertThat(searchIds(null, JAN_15.plusDays(1), JAN_15.plusDays(2)))
                .containsExactlyInAnyOrder(carA.getId(), carB.getId());
    }

    @Test
    void searchByLocation_returnsOnlyCarsAtThatLocation() {
        Car carC = car(carA.getBrand(), losAngeles, "VIN-C");
        em.flush();

        List<Car> result = carRepository.search(Car.CarStatus.AVAILABLE, null, losAngeles.getId());

        assertThat(result).extracting(Car::getId).containsExactly(carC.getId());
    }

    @Test
    void search_excludesOutOfServiceCars() {
        carB.setStatus(Car.CarStatus.OUT_OF_SERVICE);
        em.flush();

        List<Car> result = carRepository.search(Car.CarStatus.AVAILABLE, null, null);

        assertThat(result).extracting(Car::getId).containsExactly(carA.getId());
    }

    private boolean overlaps(Car car, LocalDate start, LocalDate end) {
        return bookingRepository.countOverlappingBookings(car.getId(), start, end, BookingStatus.CANCELLED) > 0;
    }

    private List<Long> searchIds(Long locationId, LocalDate start, LocalDate end) {
        return carRepository.searchAvailableBetween(Car.CarStatus.AVAILABLE, BookingStatus.CANCELLED,
                        null, locationId, start, end)
                .stream().map(Car::getId).toList();
    }

    private Location location(String city) {
        Location location = new Location();
        location.setAddress("1 Test Street");
        location.setCity(city);
        return em.persist(location);
    }

    private Car car(Brand brand, Location location, String vin) {
        Car car = new Car();
        car.setBrand(brand);
        car.setLocation(location);
        car.setMake("Toyota");
        car.setModel("Camry");
        car.setType("Sedan");
        car.setVin(vin);
        car.setPricePerDay(new BigDecimal("50.00"));
        car.setStatus(Car.CarStatus.AVAILABLE);
        return em.persist(car);
    }
}
