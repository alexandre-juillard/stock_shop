package fr.stockshop.stock_api.shoppinglist.dto;

import java.util.List;

public record FinishShoppingListResponse(
    int processedCount, List<FinishShoppingListItemResultResponse> results) {

  public FinishShoppingListResponse {
    results = List.copyOf(results);
  }

  @Override
  public List<FinishShoppingListItemResultResponse> results() {
    return List.copyOf(results);
  }
}
