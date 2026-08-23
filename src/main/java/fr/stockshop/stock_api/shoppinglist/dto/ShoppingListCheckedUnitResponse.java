package fr.stockshop.stock_api.shoppinglist.dto;

import java.util.UUID;

public record ShoppingListCheckedUnitResponse(UUID id, String code, String label) {}
