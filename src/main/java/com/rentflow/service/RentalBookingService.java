package com.rentflow.service;

import com.rentflow.dto.BookingRequestDTO;
import com.rentflow.dto.BookingResponseDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalBookingService {

    private final RentalBookingRepository bookingRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional
    public BookingResponseDTO createBooking(BookingRequestDTO request, String username) {
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be on or before end date");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Locks the car row so concurrent requests for the same car are handled one at a time
        Car car = carRepository.findByIdForUpdate(request.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with id: " + request.getCarId()));

        if (car.getStatus() != Car.CarStatus.AVAILABLE) {
            throw new CarUnavailableException("Car with id " + car.getId() + " is out of service");
        }

        Location pickup = findLocation(request.getPickupLocationId(), "Pickup");
        Location dropoff = findLocation(request.getDropoffLocationId(), "Dropoff");

        if (!car.getLocation().getId().equals(pickup.getId())) {
            throw new IllegalArgumentException("Car with id " + car.getId()
                    + " can only be picked up at location id " + car.getLocation().getId());
        }

        long overlapping = bookingRepository.countOverlappingBookings(
                car.getId(), start, end, BookingStatus.CANCELLED);
        if (overlapping > 0) {
            throw new BookingOverlapException(
                    "Car with id " + car.getId() + " is already booked for the requested date range");
        }

        RentalBooking booking = new RentalBooking();
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalCost(car.getPricePerDay().multiply(BigDecimal.valueOf(rentalDays(start, end))));
        booking.setUser(user);
        booking.setCar(car);
        booking.setPickupLocation(pickup);
        booking.setDropoffLocation(dropoff);

        return toResponse(bookingRepository.save(booking));
    }

    /** Admin only (enforced in the controller). PENDING -> CONFIRMED, and issues the invoice. */
    @Transactional
    public BookingResponseDTO confirmBooking(Long bookingId) {
        RentalBooking booking = findBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Only PENDING bookings can be confirmed; booking "
                    + bookingId + " is " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CONFIRMED);

        Invoice invoice = new Invoice();
        invoice.setIssueDate(LocalDate.now());
        invoice.setAmount(booking.getTotalCost());
        invoice.setBooking(booking);
        booking.setInvoice(invoiceRepository.save(invoice));

        return toResponse(booking);
    }

    /** The booking's owner or an admin can cancel a PENDING or CONFIRMED booking. */
    @Transactional
    public BookingResponseDTO cancelBooking(Long bookingId, String username, boolean isAdmin) {
        RentalBooking booking = findBooking(bookingId);
        checkAccess(booking, username, isAdmin);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Booking " + bookingId + " is already cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return toResponse(booking);
    }

    public List<BookingResponseDTO> getAllBookings() {
        return bookingRepository.findAllByOrderByStartDateDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<BookingResponseDTO> getBookingsForUser(String username) {
        return bookingRepository.findByUserUsernameOrderByStartDateDesc(username).stream()
                .map(this::toResponse)
                .toList();
    }

    public BookingResponseDTO getBookingById(Long id, String username, boolean isAdmin) {
        RentalBooking booking = findBooking(id);
        checkAccess(booking, username, isAdmin);
        return toResponse(booking);
    }

    /** Both the first and the last day are paid days, so a same-day rental costs one day. */
    static long rentalDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void checkAccess(RentalBooking booking, String username, boolean isAdmin) {
        if (!isAdmin && !booking.getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("You can only access your own bookings");
        }
    }

    private RentalBooking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
    }

    private Location findLocation(Long id, String label) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(label + " location not found with id: " + id));
    }

    private BookingResponseDTO toResponse(RentalBooking booking) {
        BookingResponseDTO dto = new BookingResponseDTO();
        dto.setId(booking.getId());
        dto.setStartDate(booking.getStartDate());
        dto.setEndDate(booking.getEndDate());
        dto.setStatus(booking.getStatus());
        dto.setTotalCost(booking.getTotalCost());
        dto.setDurationInDays(rentalDays(booking.getStartDate(), booking.getEndDate()));

        dto.setCustomerUsername(booking.getUser().getUsername());
        dto.setCustomerEmail(booking.getUser().getEmail());

        Car car = booking.getCar();
        dto.setCarId(car.getId());
        dto.setCarMake(car.getMake());
        dto.setCarModel(car.getModel());
        dto.setCarType(car.getType());

        dto.setPickupLocationId(booking.getPickupLocation().getId());
        dto.setPickupAddress(booking.getPickupLocation().getAddress());
        dto.setPickupCity(booking.getPickupLocation().getCity());

        dto.setDropoffLocationId(booking.getDropoffLocation().getId());
        dto.setDropoffAddress(booking.getDropoffLocation().getAddress());
        dto.setDropoffCity(booking.getDropoffLocation().getCity());

        if (booking.getInvoice() != null) {
            dto.setInvoiceId(booking.getInvoice().getId());
        }
        return dto;
    }
}
