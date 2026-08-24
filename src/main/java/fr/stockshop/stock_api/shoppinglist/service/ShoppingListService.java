package fr.stockshop.stock_api.shoppinglist.service;

import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.ShoppingListItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.ShoppingListItemNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.shoppinglist.dto.AddShoppingListItemRequest;
import fr.stockshop.stock_api.shoppinglist.dto.CheckThresholdAddedProductResponse;
import fr.stockshop.stock_api.shoppinglist.dto.CheckThresholdsResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategoryGroupResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategorySummaryResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListItemResponse;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.mapper.ShoppingListMapper;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.ArrayList;
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
