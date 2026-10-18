package com.rentflow.repository;

import com.rentflow.entity.RentalBooking;
import com.rentflow.entity.RentalBooking.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RentalBookingRepository extends JpaRepository<RentalBooking, Long> {

    @EntityGraph(attributePaths = {"user", "car", "pickupLocation", "dropoffLocation", "invoice"})
    List<RentalBooking> findAllByOrderByStartDateDesc();

    @EntityGraph(attributePaths = {"user", "car", "pickupLocation", "dropoffLocation", "invoice"})
    List<RentalBooking> findByUserUsernameOrderByStartDateDesc(String username);

    /**
     * Counts bookings of a car that overlap the inclusive range [startDate, endDate].
     * Two ranges overlap when each one starts on or before the day the other ends.
     */
    @Query("""
            SELECT COUNT(b) FROM RentalBooking b
            WHERE b.car.id = :carId
              AND b.status <> :ignoredStatus
              AND b.startDate <= :endDate
              AND b.endDate >= :startDate
            """)
    long countOverlappingBookings(@Param("carId") Long carId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate,
                                  @Param("ignoredStatus") BookingStatus ignoredStatus);
}
