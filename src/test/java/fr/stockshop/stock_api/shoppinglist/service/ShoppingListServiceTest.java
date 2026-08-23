package fr.stockshop.stock_api.shoppinglist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategoryGroupResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListCategorySummaryResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListItemResponse;
import fr.stockshop.stock_api.shoppinglist.dto.ShoppingListProductSummaryResponse;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.mapper.ShoppingListMapper;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

  @Mock private ShoppingListItemRepository shoppingListItemRepository;
  @Mock private ShoppingListMapper shoppingListMapper;

  @InjectMocks private ShoppingListService shoppingListService;

  @Test
  void listShoppingListGroupsItemsByCategoryInEncounterOrder() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();

    Category fruits = category("Fruits", "#00AA00");
    Category legumes = category("Legumes", "#FF8800");

    ShoppingListItem banane = item("Banane", fruits);
    ShoppingListItem pomme = item("Pomme", fruits);
    ShoppingListItem carotte = item("Carotte", legumes);

    ShoppingListCategorySummaryResponse fruitsResponse =
        new ShoppingListCategorySummaryResponse("Fruits", "#00AA00");
    ShoppingListCategorySummaryResponse legumesResponse =
        new ShoppingListCategorySummaryResponse("Legumes", "#FF8800");

    ShoppingListItemResponse bananeResponse =
        new ShoppingListItemResponse(
            banane.getId(),
            new ShoppingListProductSummaryResponse(banane.getProduct().getId(), "Banane"),
            false,
            null,
            null,
            true,
            Instant.parse("2026-08-23T08:00:00Z"));
    ShoppingListItemResponse pommeResponse =
        new ShoppingListItemResponse(
            pomme.getId(),
            new ShoppingListProductSummaryResponse(pomme.getProduct().getId(), "Pomme"),
            false,
            null,
            null,
            false,
            Instant.parse("2026-08-23T08:01:00Z"));
    ShoppingListItemResponse carotteResponse =
        new ShoppingListItemResponse(
            carotte.getId(),
            new ShoppingListProductSummaryResponse(carotte.getProduct().getId(), "Carotte"),
            false,
            null,
            null,
            false,
            Instant.parse("2026-08-23T08:02:00Z"));

    when(shoppingListItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of(banane, pomme, carotte));
    when(shoppingListMapper.toCategoryResponse(fruits)).thenReturn(fruitsResponse);
    when(shoppingListMapper.toCategoryResponse(legumes)).thenReturn(legumesResponse);
    when(shoppingListMapper.toItemResponse(banane)).thenReturn(bananeResponse);
    when(shoppingListMapper.toItemResponse(pomme)).thenReturn(pommeResponse);
    when(shoppingListMapper.toItemResponse(carotte)).thenReturn(carotteResponse);

    List<ShoppingListCategoryGroupResponse> result =
        shoppingListService.listShoppingList(currentUser);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).category()).isEqualTo(fruitsResponse);
    assertThat(result.get(0).items()).containsExactly(bananeResponse, pommeResponse);
    assertThat(result.get(1).category()).isEqualTo(legumesResponse);
    assertThat(result.get(1).items()).containsExactly(carotteResponse);

    verify(shoppingListMapper, times(1)).toCategoryResponse(fruits);
    verify(shoppingListMapper, times(1)).toCategoryResponse(legumes);
  }

  @Test
  void listShoppingListReturnsEmptyListWhenNoItems() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    when(shoppingListItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of());

    List<ShoppingListCategoryGroupResponse> result =
        shoppingListService.listShoppingList(currentUser);

    assertThat(result).isEmpty();
  }

  @Test
  void listShoppingListUsesRepositoryScopedToCurrentUser() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    when(shoppingListItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of());

    shoppingListService.listShoppingList(currentUser);

    verify(shoppingListItemRepository).findVisibleByUserOrderByCategoryAndProductName(currentUser);
  }

  private Category category(String name, String color) {
    return Category.builder().id(UUID.randomUUID()).name(name).color(color).build();
  }

  private ShoppingListItem item(String productName, Category category) {
    Product product =
        Product.builder().id(UUID.randomUUID()).name(productName).category(category).build();
    return ShoppingListItem.builder()
        .id(UUID.randomUUID())
        .product(product)
        .addedAt(Instant.now())
        .build();
  }
}
