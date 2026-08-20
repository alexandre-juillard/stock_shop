package fr.stockshop.stock_api.stock.dto;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import java.util.UUID;

public record StockItemProductSummary(
    UUID id, String name, CategoryResponse category, QuantityUnitResponse baseUnit) {}
