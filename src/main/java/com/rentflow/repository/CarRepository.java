package com.rentflow.repository;

import com.rentflow.entity.Car;
import com.rentflow.entity.Car.CarStatus;
import com.rentflow.entity.RentalBooking.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CarRepository extends JpaRepository<Car, Long> {

    @Override
    @EntityGraph(attributePaths = {"brand", "location"})
    List<Car> findAll();

    @EntityGraph(attributePaths = {"brand", "location"})
    List<Car> findByStatus(CarStatus status);

    @EntityGraph(attributePaths = {"brand", "location"})
    List<Car> findByBrandId(Long brandId);

    /**
     * Loads a car and locks its row until the transaction ends, so two
     * simultaneous bookings for the same car cannot both pass the overlap check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Car c WHERE c.id = :id")
    Optional<Car> findByIdForUpdate(@Param("id") Long id);

    /** Cars with the given status, optionally filtered by brand and pickup location. */
    @EntityGraph(attributePaths = {"brand", "location"})
    @Query("""
            SELECT c FROM Car c
            WHERE c.status = :status
              AND (:brandId IS NULL OR c.brand.id = :brandId)
              AND (:locationId IS NULL OR c.location.id = :locationId)
            """)
    List<Car> search(@Param("status") CarStatus status,
                     @Param("brandId") Long brandId,
                     @Param("locationId") Long locationId);

    /**
     * Like {@link #search} but also excludes cars that have a non-ignored booking
     * overlapping the inclusive date range [startDate, endDate].
     */
    @EntityGraph(attributePaths = {"brand", "location"})
    @Query("""
            SELECT c FROM Car c
            WHERE c.status = :status
              AND (:brandId IS NULL OR c.brand.id = :brandId)
              AND (:locationId IS NULL OR c.location.id = :locationId)
              AND NOT EXISTS (
                  SELECT b FROM RentalBooking b
                  WHERE b.car = c
                    AND b.status <> :ignoredStatus
                    AND b.startDate <= :endDate
                    AND b.endDate >= :startDate
              )
            """)
    List<Car> searchAvailableBetween(@Param("status") CarStatus status,
                                     @Param("ignoredStatus") BookingStatus ignoredStatus,
                                     @Param("brandId") Long brandId,
                                     @Param("locationId") Long locationId,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);
}
