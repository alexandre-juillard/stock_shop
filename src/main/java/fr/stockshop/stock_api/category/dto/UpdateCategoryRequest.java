package fr.stockshop.stock_api.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCategoryRequest(
    @NotBlank(message = "{validation.category.name.required}") String name,
    @NotBlank(message = "{validation.category.color.required}")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "{validation.category.color.invalid}")
        String color) {}
