package com.travel.accommodation.dto;

import jakarta.validation.constraints.NotBlank;

public record AccommodationSearchRequest(

        @NotBlank
        String province,

        @NotBlank
        String city,

        String town

) {
}