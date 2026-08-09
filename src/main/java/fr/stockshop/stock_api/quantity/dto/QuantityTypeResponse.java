package fr.stockshop.stock_api.quantity.dto;

import java.util.UUID;

public record QuantityTypeResponse(UUID id, String code, String label) {}
