package com.rentflow.service;

import com.rentflow.dto.BookingRequestDTO;
import com.rentflow.dto.BookingResponseDTO;
import com.rentflow.entity.Brand;
import com.rentflow.entity.Car;
import com.rentflow.entity.Invoice;
import com.rentflow.entity.Location;
import com.rentflow.entity.RentalBooking;
import com.rentflow.entity.RentalBooking.BookingStatus;
import com.rentflow.entity.User;
import com.rentflow.exception.BookingOverlapException;
import com.rentflow.exception.CarUnavailableException;
import com.rentflow.exception.InvalidBookingStateException;
import com.rentflow.exception.ResourceNotFoundException;
import com.rentflow.repository.CarRepository;
import com.rentflow.repository.InvoiceRepository;
import com.rentflow.repository.LocationRepository;
import com.rentflow.repository.RentalBookingRepository;
import com.rentflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentalBookingServiceTest {

    @Mock private RentalBookingRepository bookingRepository;
    @Mock private CarRepository carRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private InvoiceRepository invoiceRepository;

    @InjectMocks
    private RentalBookingService bookingService;

    private User customer;
    private Car car;
    private Location newYork;
    private Location losAngeles;
    private BookingRequestDTO request;

    @BeforeEach
    void setUp() {
        customer = user(1L, "customer");

        newYork = location(1L, "New York");
        losAngeles = location(2L, "Los Angeles");

        Brand toyota = new Brand();
        toyota.setId(1L);
        toyota.setName("Toyota");

        car = new Car();
        car.setId(1L);
        car.setMake("Toyota");
        car.setModel("Camry");
        car.setType("Sedan");
        car.setPricePerDay(new BigDecimal("50.00"));
        car.setStatus(Car.CarStatus.AVAILABLE);
        car.setBrand(toyota);
        car.setLocation(newYork);

        LocalDate start = LocalDate.now().plusDays(1);
        request = new BookingRequestDTO(1L, start, start.plusDays(4), 1L, 2L); // 5 rental days
    }

    @Test
    void createBooking_validRequest_createsPendingBookingWithCorrectCost() {
        stubLookups();
        when(bookingRepository.countOverlappingBookings(eq(1L), any(), any(), eq(BookingStatus.CANCELLED)))
                .thenReturn(0L);
        when(bookingRepository.save(any(RentalBooking.class))).thenAnswer(invocation -> {
            RentalBooking saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        BookingResponseDTO result = bookingService.createBooking(request, "customer");

        assertEquals(10L, result.getId());
        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertEquals(5L, result.getDurationInDays());
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTotalCost()));
        assertEquals("customer", result.getCustomerUsername());
        assertNull(result.getInvoiceId(), "Invoice is only issued on confirmation");
        // Regression: booking a future date must not take the car out of service
        assertEquals(Car.CarStatus.AVAILABLE, car.getStatus());
    }

    @Test
    void createBooking_sameStartAndEndDay_chargesOneDay() {
        request.setEndDate(request.getStartDate());
        stubLookups();
        when(bookingRepository.countOverlappingBookings(any(), any(), any(), any())).thenReturn(0L);
        when(bookingRepository.save(any(RentalBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponseDTO result = bookingService.createBooking(request, "customer");

        assertEquals(1L, result.getDurationInDays());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getTotalCost()));
    }

    @Test
    void createBooking_overlappingBooking_throwsAndSavesNothing() {
        stubLookups();
        when(bookingRepository.countOverlappingBookings(any(), any(), any(), any())).thenReturn(1L);

        BookingOverlapException ex = assertThrows(BookingOverlapException.class,
                () -> bookingService.createBooking(request, "customer"));

        assertTrue(ex.getMessage().contains("already booked"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_startAfterEnd_throwsBeforeTouchingTheDatabase() {
        request.setEndDate(request.getStartDate().minusDays(1));

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, "customer"));
        verifyNoInteractions(userRepository, carRepository, bookingRepository);
    }

    @Test
    void createBooking_carOutOfService_throws() {
        car.setStatus(Car.CarStatus.OUT_OF_SERVICE);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(customer));
        when(carRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(car));

        assertThrows(CarUnavailableException.class, () -> bookingService.createBooking(request, "customer"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_pickupNotAtCarsLocation_throws() {
        request.setPickupLocationId(2L);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(customer));
        when(carRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(car));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(losAngeles));

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, "customer"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_unknownCar_throwsNotFound() {
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(customer));
        when(carRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(request, "customer"));
    }

    @Test
    void confirmBooking_pending_confirmsAndIssuesInvoice() {
        RentalBooking booking = booking(5L, customer, BookingStatus.PENDING);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(99L);
            return invoice;
        });

        BookingResponseDTO result = bookingService.confirmBooking(5L);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals(99L, result.getInvoiceId());
        verify(invoiceRepository).save(argThat((Invoice invoice) ->
                invoice.getAmount().compareTo(booking.getTotalCost()) == 0 && invoice.getBooking() == booking));
    }

    @Test
    void confirmBooking_cancelled_throwsInvalidState() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, customer, BookingStatus.CANCELLED)));

        assertThrows(InvalidBookingStateException.class, () -> bookingService.confirmBooking(5L));
        verifyNoInteractions(invoiceRepository);
    }

    @Test
    void cancelBooking_ownBooking_cancels() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, customer, BookingStatus.CONFIRMED)));

        BookingResponseDTO result = bookingService.cancelBooking(5L, "customer", false);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelBooking_alreadyCancelled_throwsInvalidState() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, customer, BookingStatus.CANCELLED)));

        assertThrows(InvalidBookingStateException.class, () -> bookingService.cancelBooking(5L, "customer", false));
    }

    @Test
    void cancelBooking_someoneElsesBooking_throwsAccessDenied() {
        RentalBooking booking = booking(5L, user(2L, "other"), BookingStatus.PENDING);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> bookingService.cancelBooking(5L, "customer", false));
        assertEquals(BookingStatus.PENDING, booking.getStatus());
    }

    @Test
    void getBookingById_someoneElsesBooking_throwsAccessDenied() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, user(2L, "other"), BookingStatus.PENDING)));

        assertThrows(AccessDeniedException.class, () -> bookingService.getBookingById(5L, "customer", false));
    }

    @Test
    void getBookingById_adminCanViewAnyBooking() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, user(2L, "other"), BookingStatus.PENDING)));

        BookingResponseDTO result = bookingService.getBookingById(5L, "admin", true);

        assertEquals("other", result.getCustomerUsername());
    }

    @Test
    void getBookingById_unknownId_throwsNotFound() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.getBookingById(404L, "customer", false));
    }

    private void stubLookups() {
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(customer));
        when(carRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(car));
        when(locationRepository.findById(request.getPickupLocationId())).thenReturn(Optional.of(newYork));
        when(locationRepository.findById(request.getDropoffLocationId())).thenReturn(Optional.of(losAngeles));
    }

    private RentalBooking booking(Long id, User owner, BookingStatus status) {
        RentalBooking booking = new RentalBooking();
        booking.setId(id);
        booking.setUser(owner);
        booking.setCar(car);
        booking.setStatus(status);
        booking.setStartDate(LocalDate.now().plusDays(1));
        booking.setEndDate(LocalDate.now().plusDays(3));
        booking.setTotalCost(new BigDecimal("150.00"));
        booking.setPickupLocation(newYork);
        booking.setDropoffLocation(losAngeles);
        return booking;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setRole(User.Role.CUSTOMER);
        return user;
    }

    private static Location location(Long id, String city) {
        Location location = new Location();
        location.setId(id);
        location.setAddress("1 Test Street");
        location.setCity(city);
        return location;
    }
}
