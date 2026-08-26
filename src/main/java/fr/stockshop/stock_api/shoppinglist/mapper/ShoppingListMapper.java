package fr.stockshop.stock_api.shoppinglist.mapper;

import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategorySummaryResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCheckedUnitResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListItemResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListProductSummaryResponse;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import org.springframework.stereotype.Component;

@Component
public class ShoppingListMapper {

  public ShoppingListCategorySummaryResponse toCategoryResponse(Category category) {
    return new ShoppingListCategorySummaryResponse(category.getName(), category.getColor());
  }

  public ShoppingListItemResponse toItemResponse(ShoppingListItem item) {
    return new ShoppingListItemResponse(
        item.getId(),
        new ShoppingListProductSummaryResponse(
            item.getProduct().getId(), item.getProduct().getName()),
        item.isChecked(),
        item.getCheckedQuantity(),
        toCheckedUnitResponse(item.getCheckedUnit()),
        item.isAddedAutomatically(),
        item.getAddedAt(),
        item.getCheckedAt());
  }

  private ShoppingListCheckedUnitResponse toCheckedUnitResponse(QuantityUnit unit) {
    if (unit == null) {
      return null;
    }
    return new ShoppingListCheckedUnitResponse(unit.getId(), unit.getCode(), unit.getLabel());
  }
}
