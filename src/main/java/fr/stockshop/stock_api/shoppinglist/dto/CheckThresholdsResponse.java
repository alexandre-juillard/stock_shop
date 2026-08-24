package fr.stockshop.stock_api.shoppinglist.dto;

import java.util.List;

public record CheckThresholdsResponse(
    int addedCount, List<CheckThresholdAddedProductResponse> addedProducts) {

  public CheckThresholdsResponse {
    addedProducts = List.copyOf(addedProducts);
  }

  @Override
  public List<CheckThresholdAddedProductResponse> addedProducts() {
    return List.copyOf(addedProducts);
  }
}
