package fr.stockshop.stock_api.stock.mapper;

import fr.stockshop.stock_api.category.mapper.CategoryMapper;
import fr.stockshop.stock_api.quantity.mapper.QuantityReferenceMapper;
import fr.stockshop.stock_api.stock.dto.StockItemProductSummary;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockItemMapper {

  private final CategoryMapper categoryMapper;
  private final QuantityReferenceMapper quantityReferenceMapper;

  public StockItemResponse toResponse(StockItem stockItem, int expirationAlertDays) {
    StockItemStatus status = computeStatus(stockItem, expirationAlertDays);
    StockItemProductSummary productSummary =
        new StockItemProductSummary(
            stockItem.getProduct().getId(),
            stockItem.getProduct().getName(),
            categoryMapper.toResponse(stockItem.getProduct().getCategory()),
            quantityReferenceMapper.toUnitResponse(stockItem.getProduct().getBaseUnit()));

    return new StockItemResponse(
        stockItem.getId(),
        productSummary,
        stockItem.getQuantity(),
        stockItem.getLowThreshold(),
        stockItem.getExpirationDate(),
        status,
        status == StockItemStatus.EXPIRED);
  }

  /**
   * Priorité en cas de statuts cumulables (ex : item à la fois expiré et sous le seuil bas) :
   * expired &gt; expiring &gt; low &gt; ok.
   */
  private StockItemStatus computeStatus(StockItem stockItem, int expirationAlertDays) {
    LocalDate expirationDate = stockItem.getExpirationDate();
    if (expirationDate != null) {
      LocalDate today = LocalDate.now();
      if (expirationDate.isBefore(today)) {
        return StockItemStatus.EXPIRED;
      }
      if (!expirationDate.isAfter(today.plusDays(expirationAlertDays))) {
        return StockItemStatus.EXPIRING;
      }
    }

    if (stockItem.getLowThreshold() != null
        && stockItem.getQuantity().compareTo(stockItem.getLowThreshold()) <= 0) {
      return StockItemStatus.LOW;
    }

    return StockItemStatus.OK;
  }
}
