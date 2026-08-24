package fr.stockshop.stock_api.shoppinglist.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddShoppingListItemRequest(
    @NotNull(message = "{validation.shoppingListItem.productId.required}") UUID productId) {}
