package fr.stockshop.stock_api.stock.service;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.ShoppingListItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.StockItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.StockItemNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.stock.dto.CreateStockItemRequest;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemExpirationRequest;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemQuantityRequest;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemThresholdRequest;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import fr.stockshop.stock_api.stock.mapper.StockItemMapper;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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

    applyShoppingListRule(currentUser, saved);

    return stockItemMapper.toResponse(saved, currentUser.getExpirationAlertDays());
  }

  @Transactional
  public StockItemResponse updateQuantity(
      User currentUser, UUID stockItemId, UpdateStockItemQuantityRequest request) {
    StockItem stockItem =
        stockItemRepository
            .findById(stockItemId)
            .orElseThrow(() -> new StockItemNotFoundException(stockItemId));
    assertOwnership(stockItem, currentUser);

    stockItem.setQuantity(request.quantity());
    StockItem saved = stockItemRepository.save(stockItem);

    applyShoppingListRule(currentUser, saved);

    return stockItemMapper.toResponse(saved, currentUser.getExpirationAlertDays());
  }

  @Transactional
  public StockItemResponse updateThreshold(
      User currentUser, UUID stockItemId, UpdateStockItemThresholdRequest request) {
    StockItem stockItem =
        stockItemRepository
            .findById(stockItemId)
            .orElseThrow(() -> new StockItemNotFoundException(stockItemId));
    assertOwnership(stockItem, currentUser);

    stockItem.setLowThreshold(request.lowThreshold());
    StockItem saved = stockItemRepository.save(stockItem);

    applyShoppingListRule(currentUser, saved);

    return stockItemMapper.toResponse(saved, currentUser.getExpirationAlertDays());
  }

  @Transactional
  public StockItemResponse updateExpiration(
      User currentUser, UUID stockItemId, UpdateStockItemExpirationRequest request) {
    StockItem stockItem =
        stockItemRepository
            .findById(stockItemId)
            .orElseThrow(() -> new StockItemNotFoundException(stockItemId));
    assertOwnership(stockItem, currentUser);

    stockItem.setExpirationDate(request.expirationDate());
    StockItem saved = stockItemRepository.save(stockItem);

    return stockItemMapper.toResponse(saved, currentUser.getExpirationAlertDays());
  }

  @Transactional
  public void deleteStockItem(User currentUser, UUID stockItemId) {
    StockItem stockItem =
        stockItemRepository
            .findById(stockItemId)
            .orElseThrow(() -> new StockItemNotFoundException(stockItemId));
    assertOwnership(stockItem, currentUser);

    stockItemRepository.delete(stockItem);
  }

  @Transactional
  public void addToShoppingListManually(User currentUser, UUID stockItemId) {
    StockItem stockItem =
        stockItemRepository
            .findById(stockItemId)
            .orElseThrow(() -> new StockItemNotFoundException(stockItemId));
    assertOwnership(stockItem, currentUser);

    if (shoppingListItemRepository.existsByUserAndProduct(currentUser, stockItem.getProduct())) {
      throw new ShoppingListItemAlreadyExistsException(stockItem.getProduct().getName());
    }

    addToShoppingList(currentUser, stockItem.getProduct(), false);
  }

  /**
   * ajoute automatiquement l'ingrédient à la liste de courses si sa quantité est désormais
   * inférieure ou égale au seuil bas défini, sauf s'il y figure déjà. Ne retire jamais un
   * ingrédient déjà présent, même si le seuil est supprimé (mis à null).
   */
  private void applyShoppingListRule(User currentUser, StockItem stockItem) {
    boolean isBelowThreshold =
        stockItem.getLowThreshold() != null
            && stockItem.getQuantity().compareTo(stockItem.getLowThreshold()) <= 0;

    if (isBelowThreshold
        && !shoppingListItemRepository.existsByUserAndProduct(
            currentUser, stockItem.getProduct())) {
      addToShoppingList(currentUser, stockItem.getProduct(), true);
    }
  }

  private void addToShoppingList(User currentUser, Product product, boolean addedAutomatically) {
    ShoppingListItem shoppingListItem =
        ShoppingListItem.builder()
            .user(currentUser)
            .product(product)
            .checked(false)
            .addedAutomatically(addedAutomatically)
            .build();
    shoppingListItemRepository.save(shoppingListItem);
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

  private void assertOwnership(StockItem stockItem, User currentUser) {
    if (!stockItem.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Stock item does not belong to current user");
    }
  }
}
