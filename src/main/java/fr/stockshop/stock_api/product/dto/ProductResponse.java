package fr.stockshop.stock_api.product.dto;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityTypeResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    CategoryResponse category,
    QuantityTypeResponse quantityType,
    QuantityUnitResponse baseUnit,
    boolean isVisible) {}
