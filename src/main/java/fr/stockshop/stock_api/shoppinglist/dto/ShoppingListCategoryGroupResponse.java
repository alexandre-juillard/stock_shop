package fr.stockshop.stock_api.shoppinglist.dto;

import java.util.List;

public record ShoppingListCategoryGroupResponse(
    ShoppingListCategorySummaryResponse category, List<ShoppingListItemResponse> items) {

  public ShoppingListCategoryGroupResponse {
    items = List.copyOf(items);
  }

  @Override
  public List<ShoppingListItemResponse> items() {
    return List.copyOf(items);
  }
}
