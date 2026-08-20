package fr.stockshop.stock_api.stock.service;

import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import fr.stockshop.stock_api.stock.mapper.StockItemMapper;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockItemService {

  private final StockItemRepository stockItemRepository;
  private final StockItemMapper stockItemMapper;

  @Transactional(readOnly = true)
  public List<StockItemResponse> listStockItems(User currentUser, Boolean expiringSoon) {
    List<StockItemResponse> responses = mapAll(currentUser);

    if (Boolean.TRUE.equals(expiringSoon)) {
      return responses.stream().filter(StockItemService::isExpiringOrExpired).toList();
    }
    return responses;
  }

  @Transactional(readOnly = true)
  public List<StockItemResponse> listExpiringSoon(User currentUser) {
    return mapAll(currentUser).stream()
        .filter(StockItemService::isExpiringOrExpired)
        .sorted(Comparator.comparing(StockItemResponse::expirationDate))
        .toList();
  }

  private List<StockItemResponse> mapAll(User currentUser) {
    List<StockItem> stockItems =
        stockItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser);
    int expirationAlertDays = currentUser.getExpirationAlertDays();
    return stockItems.stream()
        .map(item -> stockItemMapper.toResponse(item, expirationAlertDays))
        .toList();
  }

  private static boolean isExpiringOrExpired(StockItemResponse response) {
    return response.status() == StockItemStatus.EXPIRING
        || response.status() == StockItemStatus.EXPIRED;
  }
}
