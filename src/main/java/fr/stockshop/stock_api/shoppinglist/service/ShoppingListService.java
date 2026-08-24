package fr.stockshop.stock_api.shoppinglist.service;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.QuantityUnitNotFoundException;
import fr.stockshop.stock_api.exception.ShoppingListCheckedUnitMismatchException;
import fr.stockshop.stock_api.exception.ShoppingListFinishIncompleteItemsException;
import fr.stockshop.stock_api.exception.ShoppingListItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.ShoppingListItemNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import fr.stockshop.stock_api.shoppinglist.dto.AddShoppingListItemRequest;
import fr.stockshop.stock_api.shoppinglist.dto.CheckShoppingListItemRequest;
import fr.stockshop.stock_api.shoppinglist.dto.CheckThresholdAddedProductResponse;
import fr.stockshop.stock_api.shoppinglist.dto.CheckThresholdsResponse;
import fr.stockshop.stock_api.shoppinglist.dto.FinishShoppingListItemResultResponse;
import fr.stockshop.stock_api.shoppinglist.dto.FinishShoppingListResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategoryGroupResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategorySummaryResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListItemResponse;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.mapper.ShoppingListMapper;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

  private final ShoppingListItemRepository shoppingListItemRepository;
  private final ProductRepository productRepository;
  private final QuantityUnitRepository quantityUnitRepository;
  private final StockItemRepository stockItemRepository;
  private final ShoppingListMapper shoppingListMapper;

  @Transactional(readOnly = true)
  public List<ShoppingListCategoryGroupResponse> listShoppingList(User currentUser) {
    List<ShoppingListItem> items =
        shoppingListItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser);

    Map<UUID, CategoryGroupAccumulator> groups = new LinkedHashMap<>();
    for (ShoppingListItem item : items) {
      UUID categoryId = item.getProduct().getCategory().getId();
      CategoryGroupAccumulator group =
          groups.computeIfAbsent(
              categoryId,
              ignored ->
                  new CategoryGroupAccumulator(
                      shoppingListMapper.toCategoryResponse(item.getProduct().getCategory())));
      group.items().add(shoppingListMapper.toItemResponse(item));
    }

    return groups.values().stream()
        .map(group -> new ShoppingListCategoryGroupResponse(group.category(), group.items()))
        .toList();
  }

  @Transactional
  public ShoppingListItemResponse addItem(User currentUser, AddShoppingListItemRequest request) {
    Product product =
        productRepository
            .findByIdAndUserAndVisibleTrue(request.productId(), currentUser)
            .orElseThrow(() -> new ProductNotFoundException(request.productId()));

    ShoppingListItem saved = saveNewItem(currentUser, product, false);
    return shoppingListMapper.toItemResponse(saved);
  }

  @Transactional
  public void deleteItem(User currentUser, UUID shoppingListItemId) {
    ShoppingListItem shoppingListItem =
        shoppingListItemRepository
            .findById(shoppingListItemId)
            .orElseThrow(() -> new ShoppingListItemNotFoundException(shoppingListItemId));
    assertOwnership(shoppingListItem, currentUser);
    shoppingListItemRepository.delete(shoppingListItem);
  }

  @Transactional
  public void clearList(User currentUser) {
    shoppingListItemRepository.deleteAllByUser(currentUser);
  }

  @Transactional
  public CheckThresholdsResponse checkThresholds(User currentUser) {
    List<CheckThresholdAddedProductResponse> addedProducts =
        shoppingListItemRepository.addMissingLowThresholdItems(currentUser.getId()).stream()
            .map(
                product ->
                    new CheckThresholdAddedProductResponse(product.getId(), product.getName()))
            .toList();

    return new CheckThresholdsResponse(addedProducts.size(), addedProducts);
  }

  @Transactional
  public ShoppingListItemResponse checkItem(
      User currentUser, UUID shoppingListItemId, CheckShoppingListItemRequest request) {
    ShoppingListItem shoppingListItem =
        shoppingListItemRepository
            .findById(shoppingListItemId)
            .orElseThrow(() -> new ShoppingListItemNotFoundException(shoppingListItemId));
    assertOwnership(shoppingListItem, currentUser);

    QuantityUnit checkedUnit =
        quantityUnitRepository
            .findById(request.checkedUnitId())
            .orElseThrow(() -> new QuantityUnitNotFoundException(request.checkedUnitId()));

    UUID productQuantityTypeId = shoppingListItem.getProduct().getQuantityType().getId();
    UUID checkedUnitQuantityTypeId = checkedUnit.getQuantityType().getId();
    if (!checkedUnitQuantityTypeId.equals(productQuantityTypeId)) {
      throw new ShoppingListCheckedUnitMismatchException(shoppingListItemId, checkedUnit.getId());
    }

    shoppingListItem.setChecked(true);
    shoppingListItem.setCheckedQuantity(request.checkedQuantity());
    shoppingListItem.setCheckedUnit(checkedUnit);
    shoppingListItem.setCheckedAt(Instant.now());

    return shoppingListMapper.toItemResponse(shoppingListItemRepository.save(shoppingListItem));
  }

  @Transactional
  public ShoppingListItemResponse uncheckItem(User currentUser, UUID shoppingListItemId) {
    ShoppingListItem shoppingListItem =
        shoppingListItemRepository
            .findById(shoppingListItemId)
            .orElseThrow(() -> new ShoppingListItemNotFoundException(shoppingListItemId));
    assertOwnership(shoppingListItem, currentUser);

    shoppingListItem.setChecked(false);
    shoppingListItem.setCheckedQuantity(null);
    shoppingListItem.setCheckedUnit(null);
    shoppingListItem.setCheckedAt(null);

    return shoppingListMapper.toItemResponse(shoppingListItemRepository.save(shoppingListItem));
  }

  @Transactional
  public FinishShoppingListResponse finishShoppingList(User currentUser) {
    List<ShoppingListItem> checkedItems =
        shoppingListItemRepository.findCheckedByUserOrderByAddedAtAsc(currentUser);
    if (checkedItems.isEmpty()) {
      return new FinishShoppingListResponse(0, List.of());
    }

    List<ShoppingListItem> incompleteItems =
        checkedItems.stream()
            .filter(item -> item.getCheckedQuantity() == null || item.getCheckedUnit() == null)
            .toList();
    if (!incompleteItems.isEmpty()) {
      String itemList =
          incompleteItems.stream()
              .map(item -> item.getProduct().getName() + "(" + item.getId() + ")")
              .toList()
              .toString();
      throw new ShoppingListFinishIncompleteItemsException(itemList);
    }

    List<UUID> productIds = checkedItems.stream().map(item -> item.getProduct().getId()).toList();
    Map<UUID, StockItem> stockByProductId = new HashMap<>();
    stockItemRepository
        .findByUserAndProduct_IdIn(currentUser, productIds)
        .forEach(stockItem -> stockByProductId.put(stockItem.getProduct().getId(), stockItem));

    List<FinishShoppingListItemResultResponse> results = new ArrayList<>();
    for (ShoppingListItem checkedItem : checkedItems) {
      Product product = checkedItem.getProduct();
      BigDecimal addedQuantityInBaseUnit = toBaseUnitQuantity(checkedItem);

      StockItem stockItem = stockByProductId.get(product.getId());
      String action;
      if (stockItem == null) {
        stockItem =
            stockItemRepository.save(
                StockItem.builder()
                    .user(currentUser)
                    .product(product)
                    .quantity(addedQuantityInBaseUnit)
                    .build());
        stockByProductId.put(product.getId(), stockItem);
        action = "created";
      } else {
        stockItem.setQuantity(stockItem.getQuantity().add(addedQuantityInBaseUnit));
        action = "updated";
      }

      results.add(
          new FinishShoppingListItemResultResponse(
              checkedItem.getId(),
              product.getId(),
              product.getName(),
              stockItem.getId(),
              action,
              addedQuantityInBaseUnit,
              stockItem.getQuantity()));
    }

    shoppingListItemRepository.deleteAllInBatch(checkedItems);
    return new FinishShoppingListResponse(results.size(), results);
  }

  private BigDecimal toBaseUnitQuantity(ShoppingListItem checkedItem) {
    QuantityUnit checkedUnit = checkedItem.getCheckedUnit();
    QuantityUnit baseUnit = checkedItem.getProduct().getBaseUnit();
    if (!checkedUnit.getQuantityType().getId().equals(baseUnit.getQuantityType().getId())) {
      throw new ShoppingListCheckedUnitMismatchException(checkedItem.getId(), checkedUnit.getId());
    }

    BigDecimal checkedInReference =
        checkedItem.getCheckedQuantity().multiply(checkedUnit.getConversionFactor());
    return checkedInReference.divide(baseUnit.getConversionFactor(), 3, RoundingMode.HALF_UP);
  }

  private ShoppingListItem saveNewItem(
      User currentUser, Product product, boolean addedAutomatically) {
    if (shoppingListItemRepository.existsByUserAndProduct(currentUser, product)) {
      throw new ShoppingListItemAlreadyExistsException(product.getName());
    }

    ShoppingListItem shoppingListItem =
        ShoppingListItem.builder()
            .user(currentUser)
            .product(product)
            .checked(false)
            .addedAutomatically(addedAutomatically)
            .build();

    try {
      return shoppingListItemRepository.saveAndFlush(shoppingListItem);
    } catch (DataIntegrityViolationException ex) {
      throw new ShoppingListItemAlreadyExistsException(product.getName());
    }
  }

  private void assertOwnership(ShoppingListItem shoppingListItem, User currentUser) {
    if (!shoppingListItem.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Shopping list item does not belong to current user");
    }
  }

  private record CategoryGroupAccumulator(
      ShoppingListCategorySummaryResponse category, List<ShoppingListItemResponse> items) {
    private CategoryGroupAccumulator(ShoppingListCategorySummaryResponse category) {
      this(category, new ArrayList<>());
    }
  }
}
