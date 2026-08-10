package fr.stockshop.stock_api.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateProductRequest(
    @NotBlank(message = "{validation.product.name.required}") String name,
    @NotNull(message = "{validation.product.categoryId.required}") UUID categoryId,
    @NotNull(message = "{validation.product.quantityTypeId.required}") UUID quantityTypeId,
    @NotNull(message = "{validation.product.baseUnitId.required}") UUID baseUnitId) {}
