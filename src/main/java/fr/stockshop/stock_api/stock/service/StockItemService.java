package fr.stockshop.stock_api.stock.service;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.StockItemAlreadyExistsException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.stock.dto.CreateStockItemRequest;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import fr.stockshop.stock_api.stock.mapper.StockItemMapper;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockItemService {

  private final StockItemRepository stockItemRepository;
  private final ProductRepository productRepository;
  private final ShoppingListItemRepository shoppingListItemRepository;
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

  @Transactional
  public StockItemResponse createStockItem(User currentUser, CreateStockItemRequest request) {
    Product product =
        productRepository
            .findById(request.productId())
            .orElseThrow(() -> new ProductNotFoundException(request.productId()));
    assertOwnership(product, currentUser);

    if (stockItemRepository.existsByUserAndProduct(currentUser, product)) {
      throw new StockItemAlreadyExistsException(product.getName());
    }

    StockItem stockItem =
        StockItem.builder()
            .user(currentUser)
            .product(product)
            .quantity(request.quantity())
            .lowThreshold(request.lowThreshold())
            .expirationDate(request.expirationDate())
            .build();

    StockItem saved;
    try {
      saved = stockItemRepository.save(stockItem);
    } catch (DataIntegrityViolationException ex) {
      throw new StockItemAlreadyExistsException(product.getName());
    }

    addToShoppingListIfBelowThreshold(currentUser, product, request);

    return stockItemMapper.toResponse(saved, currentUser.getExpirationAlertDays());
  }

  /**
   * si la quantité initiale est déjà sous le seuil bas fourni, l'ingrédient est ajouté
   * automatiquement à la liste de courses, sauf s'il y figure déjà.
   */
  private void addToShoppingListIfBelowThreshold(
      User currentUser, Product product, CreateStockItemRequest request) {
    boolean isBelowThreshold =
        request.lowThreshold() != null && request.quantity().compareTo(request.lowThreshold()) <= 0;

    if (isBelowThreshold
        && !shoppingListItemRepository.existsByUserAndProduct(currentUser, product)) {
      ShoppingListItem shoppingListItem =
          ShoppingListItem.builder()
              .user(currentUser)
              .product(product)
              .checked(false)
              .addedAutomatically(true)
              .build();
      shoppingListItemRepository.save(shoppingListItem);
    }
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

  private void assertOwnership(Product product, User currentUser) {
    if (!product.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Product does not belong to current user");
    }
  }
}
