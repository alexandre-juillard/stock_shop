package fr.stockshop.stock_api.shoppinglist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.ShoppingListItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.ShoppingListItemNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.shoppinglist.dto.AddShoppingListItemRequest;
import fr.stockshop.stock_api.shoppinglist.dto.CheckThresholdsResponse;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

  @Mock private ShoppingListItemRepository shoppingListItemRepository;
  @Mock private ProductRepository productRepository;
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

  @Test
  void addItemCreatesUncheckedManualItemAndReturnsMappedResponse() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UUID productId = UUID.randomUUID();
    Product product =
        Product.builder()
            .id(productId)
            .name("Pomme")
            .category(category("Fruits", "#00AA00"))
            .build();
    AddShoppingListItemRequest request = new AddShoppingListItemRequest(productId);

    ShoppingListItem saved =
        ShoppingListItem.builder()
            .id(UUID.randomUUID())
            .user(currentUser)
            .product(product)
            .checked(false)
            .addedAutomatically(false)
            .addedAt(Instant.parse("2026-08-24T10:00:00Z"))
            .build();
    ShoppingListItemResponse expected =
        new ShoppingListItemResponse(
            saved.getId(),
            new ShoppingListProductSummaryResponse(productId, "Pomme"),
            false,
            null,
            null,
            false,
            saved.getAddedAt());

    when(productRepository.findByIdAndUserAndVisibleTrue(productId, currentUser))
        .thenReturn(Optional.of(product));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(shoppingListItemRepository.saveAndFlush(any(ShoppingListItem.class))).thenReturn(saved);
    when(shoppingListMapper.toItemResponse(saved)).thenReturn(expected);

    ShoppingListItemResponse result = shoppingListService.addItem(currentUser, request);

    assertThat(result).isEqualTo(expected);

    ArgumentCaptor<ShoppingListItem> captor = ArgumentCaptor.forClass(ShoppingListItem.class);
    verify(shoppingListItemRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().isChecked()).isFalse();
    assertThat(captor.getValue().isAddedAutomatically()).isFalse();
    assertThat(captor.getValue().getUser()).isEqualTo(currentUser);
    assertThat(captor.getValue().getProduct()).isEqualTo(product);
  }

  @Test
  void addItemThrowsNotFoundWhenProductMissingOrInvisible() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UUID productId = UUID.randomUUID();
    AddShoppingListItemRequest request = new AddShoppingListItemRequest(productId);

    when(productRepository.findByIdAndUserAndVisibleTrue(productId, currentUser))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> shoppingListService.addItem(currentUser, request))
        .isInstanceOf(ProductNotFoundException.class);
    verify(shoppingListItemRepository, never()).saveAndFlush(any(ShoppingListItem.class));
  }

  @Test
  void addItemThrowsConflictWhenItemAlreadyExists() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UUID productId = UUID.randomUUID();
    Product product = Product.builder().id(productId).name("Pomme").build();
    AddShoppingListItemRequest request = new AddShoppingListItemRequest(productId);

    when(productRepository.findByIdAndUserAndVisibleTrue(productId, currentUser))
        .thenReturn(Optional.of(product));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);

    assertThatThrownBy(() -> shoppingListService.addItem(currentUser, request))
        .isInstanceOf(ShoppingListItemAlreadyExistsException.class);
    verify(shoppingListItemRepository, never()).saveAndFlush(any(ShoppingListItem.class));
  }

  @Test
  void addItemThrowsConflictWhenUniqueConstraintIsViolatedConcurrently() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UUID productId = UUID.randomUUID();
    Product product = Product.builder().id(productId).name("Pomme").build();
    AddShoppingListItemRequest request = new AddShoppingListItemRequest(productId);

    when(productRepository.findByIdAndUserAndVisibleTrue(productId, currentUser))
        .thenReturn(Optional.of(product));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(shoppingListItemRepository.saveAndFlush(any(ShoppingListItem.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> shoppingListService.addItem(currentUser, request))
        .isInstanceOf(ShoppingListItemAlreadyExistsException.class);
  }

  @Test
  void deleteItemRemovesItemWhenOwnedByCurrentUser() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    ShoppingListItem item =
        ShoppingListItem.builder()
            .id(UUID.randomUUID())
            .user(currentUser)
            .product(Product.builder().build())
            .build();

    when(shoppingListItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

    shoppingListService.deleteItem(currentUser, item.getId());

    verify(shoppingListItemRepository).delete(item);
  }

  @Test
  void deleteItemThrowsNotFoundWhenMissing() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UUID itemId = UUID.randomUUID();
    when(shoppingListItemRepository.findById(itemId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shoppingListService.deleteItem(currentUser, itemId))
        .isInstanceOf(ShoppingListItemNotFoundException.class);
    verify(shoppingListItemRepository, never()).delete(any(ShoppingListItem.class));
  }

  @Test
  void deleteItemThrowsForbiddenWhenOwnedByAnotherUser() {
    User owner = User.builder().id(UUID.randomUUID()).build();
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    ShoppingListItem item =
        ShoppingListItem.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .product(Product.builder().build())
            .build();

    when(shoppingListItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

    assertThatThrownBy(() -> shoppingListService.deleteItem(currentUser, item.getId()))
        .isInstanceOf(AccessDeniedException.class);
    verify(shoppingListItemRepository, never()).delete(any(ShoppingListItem.class));
  }

  @Test
  void clearListDeletesAllItemsForCurrentUser() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();

    shoppingListService.clearList(currentUser);

    verify(shoppingListItemRepository).deleteAllByUser(eq(currentUser));
  }

  @Test
  void checkThresholdsReturnsAddedProductsAndCount() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UUID product1 = UUID.randomUUID();
    UUID product2 = UUID.randomUUID();

    ShoppingListItemRepository.AddedProductProjection added1 =
        addedProductProjection(product1, "Pomme");
    ShoppingListItemRepository.AddedProductProjection added2 =
        addedProductProjection(product2, "Carotte");

    when(shoppingListItemRepository.addMissingLowThresholdItems(currentUser.getId()))
        .thenReturn(List.of(added1, added2));

    CheckThresholdsResponse response = shoppingListService.checkThresholds(currentUser);

    assertThat(response.addedCount()).isEqualTo(2);
    assertThat(response.addedProducts())
        .extracting(product -> product.id().toString(), product -> product.name())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(product1.toString(), "Pomme"),
            org.assertj.core.groups.Tuple.tuple(product2.toString(), "Carotte"));
    verify(shoppingListItemRepository).addMissingLowThresholdItems(currentUser.getId());
  }

  @Test
  void checkThresholdsReturnsEmptyPayloadWhenNothingAdded() {
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    when(shoppingListItemRepository.addMissingLowThresholdItems(currentUser.getId()))
        .thenReturn(List.of());

    CheckThresholdsResponse response = shoppingListService.checkThresholds(currentUser);

    assertThat(response.addedCount()).isZero();
    assertThat(response.addedProducts()).isEmpty();
    verify(shoppingListItemRepository).addMissingLowThresholdItems(currentUser.getId());
  }

  private ShoppingListItemRepository.AddedProductProjection addedProductProjection(
      UUID id, String name) {
    return new ShoppingListItemRepository.AddedProductProjection() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }
    };
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
