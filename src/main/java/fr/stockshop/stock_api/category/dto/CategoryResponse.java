package fr.stockshop.stock_api.category.dto;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String color) {}
