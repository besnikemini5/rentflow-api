package com.rentflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests: real HTTP layer, real security, real database (in-memory H2)
 * with the seed data from DataInitializer. Each test is rolled back afterwards.
 *
 * Seed data: car 1 Toyota Camry (50/day) and car 2 RAV4 at location 1 (New York),
 * cars 3 and 4 (BMW) at location 2 (Los Angeles). Users: admin/admin123, user/user123.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RentFlowApiTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String CUSTOMER = "user";
    private static final String CUSTOMER_PASSWORD = "user123";

    private static final long CAMRY = 1L;
    private static final long NEW_YORK = 1L;
    private static final long LOS_ANGELES = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ===== Catalogue (public) =====

    @Test
    void anyoneCanBrowseCars() throws Exception {
        mockMvc.perform(get("/api/cars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].brandName").exists())
                .andExpect(jsonPath("$[0].locationCity").exists());
    }

    @Test
    void unknownCarReturns404() throws Exception {
        mockMvc.perform(get("/api/cars/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/cars/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchByLocationReturnsOnlyCarsThere() throws Exception {
        mockMvc.perform(get("/api/cars/search").param("locationId", String.valueOf(LOS_ANGELES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].locationCity", everyItem(is("Los Angeles"))));
    }

    @Test
    void searchWithOnlyStartDateReturns400() throws Exception {
        mockMvc.perform(get("/api/cars/search").param("startDate", day(5)))
                .andExpect(status().isBadRequest());
    }

    // ===== Car status (admin only) =====

    @Test
    void anonymousUserCannotChangeCarStatus() throws Exception {
        mockMvc.perform(put("/api/cars/1/status").param("status", "OUT_OF_SERVICE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotChangeCarStatus() throws Exception {
        mockMvc.perform(put("/api/cars/1/status").param("status", "OUT_OF_SERVICE")
                        .with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeCarStatus() throws Exception {
        mockMvc.perform(put("/api/cars/1/status").param("status", "OUT_OF_SERVICE")
                        .with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
    }

    @Test
    void invalidStatusValueReturns400() throws Exception {
        mockMvc.perform(put("/api/cars/1/status").param("status", "BROKEN")
                        .with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ===== Authentication and roles =====

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/bookings/my-bookings").with(httpBasic(CUSTOMER, "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserCannotCreateBooking() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(CAMRY, 1, 3, NEW_YORK, NEW_YORK)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotListAllBookings() throws Exception {
        mockMvc.perform(get("/api/bookings").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllBookings() throws Exception {
        mockMvc.perform(get("/api/bookings").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk());
    }

    // ===== Creating bookings =====

    @Test
    void customerCanBookACar() throws Exception {
        createAs(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 5, NEW_YORK, LOS_ANGELES))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.durationInDays").value(5))
                .andExpect(jsonPath("$.totalCost").value(250.0))
                .andExpect(jsonPath("$.customerUsername").value(CUSTOMER))
                .andExpect(jsonPath("$.invoiceId").doesNotExist());
    }

    @Test
    void overlappingBookingReturns409() throws Exception {
        createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 10, 14, NEW_YORK, NEW_YORK));

        createAs(ADMIN, ADMIN_PASSWORD, bookingJson(CAMRY, 12, 16, NEW_YORK, NEW_YORK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Booking Overlap"));
    }

    @Test
    void backToBackBookingsAreAllowed() throws Exception {
        createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 10, 14, NEW_YORK, NEW_YORK));

        createAs(ADMIN, ADMIN_PASSWORD, bookingJson(CAMRY, 15, 19, NEW_YORK, NEW_YORK))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelledBookingFreesTheDates() throws Exception {
        long id = createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 10, 14, NEW_YORK, NEW_YORK));
        mockMvc.perform(put("/api/bookings/" + id + "/cancel").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isOk());

        createAs(ADMIN, ADMIN_PASSWORD, bookingJson(CAMRY, 10, 14, NEW_YORK, NEW_YORK))
                .andExpect(status().isCreated());
    }

    @Test
    void missingFieldsReturn400WithFieldErrors() throws Exception {
        createAs(CUSTOMER, CUSTOMER_PASSWORD, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void pastDateReturns400() throws Exception {
        createAs(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, -2, 3, NEW_YORK, NEW_YORK))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startAfterEndReturns400() throws Exception {
        createAs(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 5, 3, NEW_YORK, NEW_YORK))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pickupMustBeAtTheCarsLocation() throws Exception {
        createAs(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 3, LOS_ANGELES, NEW_YORK))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingAnUnknownCarReturns404() throws Exception {
        createAs(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(999, 1, 3, NEW_YORK, NEW_YORK))
                .andExpect(status().isNotFound());
    }

    @Test
    void outOfServiceCarCannotBeBooked() throws Exception {
        mockMvc.perform(put("/api/cars/1/status").param("status", "OUT_OF_SERVICE")
                        .with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk());

        createAs(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 3, NEW_YORK, NEW_YORK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Car Unavailable"));
    }

    /** Regression test: a future booking used to hide the car from searches for ALL dates. */
    @Test
    void futureBookingOnlyHidesTheCarOnThoseDates() throws Exception {
        createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 30, 35, NEW_YORK, NEW_YORK));

        mockMvc.perform(get("/api/cars/search").param("startDate", day(10)).param("endDate", day(12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) CAMRY)));

        mockMvc.perform(get("/api/cars/search").param("startDate", day(31)).param("endDate", day(32)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) CAMRY))));
    }

    // ===== Viewing, confirming and cancelling bookings =====

    @Test
    void customerCannotViewSomeoneElsesBooking() throws Exception {
        long adminsBooking = createBooking(ADMIN, ADMIN_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));

        mockMvc.perform(get("/api/bookings/" + adminsBooking).with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCancelSomeoneElsesBooking() throws Exception {
        long adminsBooking = createBooking(ADMIN, ADMIN_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));

        mockMvc.perform(put("/api/bookings/" + adminsBooking + "/cancel").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyOwnBookings() throws Exception {
        createBooking(ADMIN, ADMIN_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));
        createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 5, 6, NEW_YORK, NEW_YORK));

        mockMvc.perform(get("/api/bookings/my-bookings").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerUsername").value(CUSTOMER));
    }

    @Test
    void customerCannotConfirmBookings() throws Exception {
        long id = createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));

        mockMvc.perform(put("/api/bookings/" + id + "/confirm").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminConfirmIssuesInvoice() throws Exception {
        long id = createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));

        mockMvc.perform(put("/api/bookings/" + id + "/confirm").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.invoiceId").isNumber());
    }

    @Test
    void confirmingACancelledBookingReturns409() throws Exception {
        long id = createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));
        mockMvc.perform(put("/api/bookings/" + id + "/cancel").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/bookings/" + id + "/confirm").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid Booking State"));
    }

    @Test
    void cancellingTwiceReturns409() throws Exception {
        long id = createBooking(CUSTOMER, CUSTOMER_PASSWORD, bookingJson(CAMRY, 1, 2, NEW_YORK, NEW_YORK));
        mockMvc.perform(put("/api/bookings/" + id + "/cancel").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/bookings/" + id + "/cancel").with(httpBasic(CUSTOMER, CUSTOMER_PASSWORD)))
                .andExpect(status().isConflict());
    }

    // --- helpers ---

    private ResultActions createAs(String username, String password, String json) throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .with(httpBasic(username, password))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long createBooking(String username, String password, String json) throws Exception {
        String body = createAs(username, password, json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String bookingJson(long carId, int startInDays, int endInDays, long pickupId, long dropoffId)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("carId", carId);
        body.put("startDate", day(startInDays));
        body.put("endDate", day(endInDays));
        body.put("pickupLocationId", pickupId);
        body.put("dropoffLocationId", dropoffId);
        return objectMapper.writeValueAsString(body);
    }

    private static String day(int daysFromToday) {
        return LocalDate.now().plusDays(daysFromToday).toString();
    }
}
