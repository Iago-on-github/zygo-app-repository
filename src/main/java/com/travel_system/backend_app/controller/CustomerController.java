package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.service.CustomerCalendarService;
import com.travel_system.backend_app.service.CustomerSettingsService;
import com.travel_system.backend_app.service.profilePicture.CustomerProfilePictureService;
import com.travel_system.backend_app.utils.DateTimeFormats;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/customer")
public class CustomerController {

    private final CustomerProfilePictureService customerProfilePictureService;
    private final CustomerSettingsService customerSettingsService;
    private final CustomerCalendarService customerCalendarService;


    public CustomerController(CustomerProfilePictureService customerProfilePictureService, CustomerSettingsService customerSettingsService, CustomerCalendarService customerCalendarService, DateTimeFormats dateTimeFormats) {
        this.customerProfilePictureService = customerProfilePictureService;
        this.customerSettingsService = customerSettingsService;
        this.customerCalendarService = customerCalendarService;
    }

    @GetMapping("settings/my")
    public ResponseEntity<CustomerSettingsResponseDTO> getMyCustomer() {
        return ResponseEntity.ok().body(customerSettingsService.getMyCustomer());
    }

    @GetMapping("settings/usage")
    public ResponseEntity<CustomerPlanResponseDTO> getPlanUsage() {
        return ResponseEntity.ok().body(customerSettingsService.getPlanUsage());
    }

    @GetMapping("settings/infos")
    public ResponseEntity<CustomerInfoResponseDTO> getCustomerInfo() {
        return ResponseEntity.ok().body(customerSettingsService.getCustomerInfo());
    }

    @PatchMapping("settings/contact/update")
    public ResponseEntity<CustomerSettingsResponseDTO> updateMyContact(@Valid @RequestBody CustomerContactUpdateDTO dto) {
        return ResponseEntity.ok().body(customerSettingsService.updateMyContact(dto));
    }

    @PatchMapping("/settings/update")
    public ResponseEntity<CustomerSettingsResponseDTO> updateMySettings(@RequestBody CustomerSettingsUpdateDTO dto) {
        return ResponseEntity.ok().body(customerSettingsService.updateMySettings(dto));
    }

    @PutMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomerSettingsResponseDTO> customerProfilePictureService(@RequestParam("file") MultipartFile file) throws IOException {
        customerProfilePictureService.updateCustomerPicture(file);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/settings/logo/remove")
    public ResponseEntity<Void> deleteCustomerProfilePicture() {
        customerProfilePictureService.deleteCustomerPicture();

        return ResponseEntity.noContent().build();
    }

    /*
    * HOLIDAYS, CALENDAR
    * */

    @GetMapping("/holidays")
    public ResponseEntity<Page<CustomerHolidayResponseDTO>> getAllCustomerHolidays(
            @RequestParam(required = false) Set<Shift> shifts,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 15, sort = "date", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(customerCalendarService.getAllCustomerHolidays(dateFrom, dateTo, shifts, pageable));
    }

    @PostMapping("/holidays/new")
    public ResponseEntity<CustomerHolidayResponseDTO> createHoliday(@Valid @RequestBody CustomerCalendarRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        CustomerHolidayResponseDTO holiday = customerCalendarService.createHoliday(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(holiday.id()).toUri();

        return ResponseEntity.created(uri).body(holiday);
    }

    @PatchMapping("/holidays/{holidayId}/update")
    public ResponseEntity<CustomerHolidayResponseDTO> updateHoliday(@PathVariable UUID holidayId, @Valid @RequestBody CustomerCalendarUpdateDTO dto) {
        return ResponseEntity.ok().body(customerCalendarService.updateHoliday(holidayId, dto));
    }

    @DeleteMapping("/holidays/{holidayId}/delete")
    public ResponseEntity<Void> deleteHoliday(@PathVariable UUID holidayId) {
        customerCalendarService.deleteHoliday(holidayId);

        return ResponseEntity.noContent().build();
    }

    /*
    * TERM
    * */

    @GetMapping("/terms/current")
    public ResponseEntity<CustomerTermResponseDTO> getCurrentTerm() {
        return ResponseEntity.ok().body(customerCalendarService.getCurrentTerm());
    }

    @GetMapping("/terms/all")
    public ResponseEntity<List<CustomerTermResponseDTO>> getAllCustomerTerms() {
        return ResponseEntity.ok().body(customerCalendarService.getAllCustomerTerms());
    }

    @PostMapping("/terms/new")
    public ResponseEntity<CustomerTermResponseDTO> createCustomerTerm(@Valid @RequestBody CustomerTermRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        CustomerTermResponseDTO customerTerm = customerCalendarService.createCustomerTerm(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(customerTerm.id()).toUri();

        return ResponseEntity.created(uri).body(customerTerm);
    }

    @PatchMapping("/terms/{customerTermId}/update")
    public ResponseEntity<CustomerTermResponseDTO> updateCustomerTerm(@PathVariable UUID customerTermId, @Valid @RequestBody CustomerTermUpdateDTO dto) {
        return ResponseEntity.ok().body(customerCalendarService.updateCustomerTerm(customerTermId, dto));
    }

    @DeleteMapping("/terms/{customerTermId}/delete")
    public ResponseEntity<Void> deleteCustomerTerm(@PathVariable UUID customerTermId) {
        customerCalendarService.deleteCustomerTerm(customerTermId);

        return ResponseEntity.noContent().build();
    }
}
