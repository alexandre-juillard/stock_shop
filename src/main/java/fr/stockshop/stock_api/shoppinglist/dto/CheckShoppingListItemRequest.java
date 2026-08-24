package fr.stockshop.stock_api.shoppinglist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CheckShoppingListItemRequest(
    @NotNull(message = "{validation.shoppingListItem.checkedQuantity.required}")
        @Positive(message = "{validation.shoppingListItem.checkedQuantity.positive}")
        BigDecimal checkedQuantity,
    @NotNull(message = "{validation.shoppingListItem.checkedUnitId.required}")
        UUID checkedUnitId) {}
