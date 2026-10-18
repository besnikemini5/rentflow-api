package com.rentflow.controller;

import com.rentflow.config.OpenApiConfig;
import com.rentflow.dto.BookingRequestDTO;
import com.rentflow.dto.BookingResponseDTO;
import com.rentflow.service.RentalBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Rental Booking", description = "Create and manage rental bookings (login required)")
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH)
public class RentalBookingController {

    private final RentalBookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a booking", description = "Books a car for the logged-in user. Fails with 409 if the dates overlap an existing booking.")
    public ResponseEntity<BookingResponseDTO> createBooking(@Valid @RequestBody BookingRequestDTO request,
                                                            Authentication authentication) {
        BookingResponseDTO created = bookingService.createBooking(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all bookings (admin)")
    public List<BookingResponseDTO> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "List my bookings")
    public List<BookingResponseDTO> getMyBookings(Authentication authentication) {
        return bookingService.getBookingsForUser(authentication.getName());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Customers can only view their own bookings; admins can view any.")
    public BookingResponseDTO getBookingById(@PathVariable Long id, Authentication authentication) {
        return bookingService.getBookingById(id, authentication.getName(), isAdmin(authentication));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirm a pending booking (admin)", description = "Moves the booking to CONFIRMED and issues an invoice.")
    public BookingResponseDTO confirmBooking(@PathVariable Long id) {
        return bookingService.confirmBooking(id);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking", description = "Customers can cancel their own bookings; admins can cancel any.")
    public BookingResponseDTO cancelBooking(@PathVariable Long id, Authentication authentication) {
        return bookingService.cancelBooking(id, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
