package com.example.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AllowRequestDto {

    @NotBlank
    private String userId;

    @PositiveOrZero
    private Double timestamp; // optional; null -> server now
}
