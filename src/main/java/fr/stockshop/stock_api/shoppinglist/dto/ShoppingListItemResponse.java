package fr.stockshop.stock_api.shoppinglist.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShoppingListItemResponse(
    UUID id,
    ShoppingListProductSummaryResponse product,
    boolean isChecked,
    BigDecimal checkedQuantity,
    ShoppingListCheckedUnitResponse checkedUnit,
    boolean addedAutomatically,
    Instant addedAt) {}
