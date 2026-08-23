package fr.stockshop.stock_api.shoppinglist.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

  private final ShoppingListItemRepository shoppingListItemRepository;
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
        .map(
            group ->
                new ShoppingListCategoryGroupResponse(group.category(), List.copyOf(group.items())))
        .toList();
  }

  private record CategoryGroupAccumulator(
      ShoppingListCategorySummaryResponse category, List<ShoppingListItemResponse> items) {
    private CategoryGroupAccumulator(ShoppingListCategorySummaryResponse category) {
      this(category, new ArrayList<>());
    }
  }
}
