package fr.stockshop.stock_api.quantity.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuantityUnitResponse(
    UUID id, String code, String label, BigDecimal conversionFactor, boolean isBaseUnit) {}
