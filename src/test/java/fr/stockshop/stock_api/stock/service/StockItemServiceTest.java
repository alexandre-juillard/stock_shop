package fr.stockshop.stock_api.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.ShoppingListItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.StockItemAlreadyExistsException;
import fr.stockshop.stock_api.exception.StockItemNotFoundException;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import fr.stockshop.stock_api.shoppinglist.entity.ShoppingListItem;
import fr.stockshop.stock_api.shoppinglist.repository.ShoppingListItemRepository;
import fr.stockshop.stock_api.stock.dto.CreateStockItemRequest;
import fr.stockshop.stock_api.stock.dto.StockItemProductSummary;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemExpirationRequest;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemQuantityRequest;
import fr.stockshop.stock_api.stock.dto.UpdateStockItemThresholdRequest;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import fr.stockshop.stock_api.stock.mapper.StockItemMapper;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class StockItemServiceTest {

  @Mock private StockItemRepository stockItemRepository;
  @Mock private ProductRepository productRepository;
  @Mock private ShoppingListItemRepository shoppingListItemRepository;
  @Mock private StockItemMapper stockItemMapper;

  @InjectMocks private StockItemService stockItemService;

  private static StockItemResponse response(
      StockItemStatus status, LocalDate expirationDate, String productName) {
    return new StockItemResponse(
        UUID.randomUUID(),
        new StockItemProductSummary(
            UUID.randomUUID(),
            productName,
            new CategoryResponse(UUID.randomUUID(), "Cat", "#000000"),
            new QuantityUnitResponse(UUID.randomUUID(), "kg", "Kilogramme", BigDecimal.ONE, true)),
        BigDecimal.TEN,
        null,
        expirationDate,
        status,
        status == StockItemStatus.EXPIRED);
  }

  @Test
  void listStockItemsReturnsAllMappedItemsWhenNoFilterRequested() {
    User currentUser = User.builder().id(UUID.randomUUID()).expirationAlertDays(3).build();
    StockItem okItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItem lowItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItemResponse okResponse = response(StockItemStatus.OK, null, "Pomme");
    StockItemResponse lowResponse = response(StockItemStatus.LOW, null, "Riz");

    when(stockItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of(okItem, lowItem));
    when(stockItemMapper.toResponse(okItem, 3)).thenReturn(okResponse);
    when(stockItemMapper.toResponse(lowItem, 3)).thenReturn(lowResponse);

    List<StockItemResponse> result = stockItemService.listStockItems(currentUser, null);

    assertThat(result).containsExactly(okResponse, lowResponse);
  }

  @Test
  void listStockItemsFiltersToExpiringOrExpiredWhenExpiringSoonIsTrue() {
    User currentUser = User.builder().id(UUID.randomUUID()).expirationAlertDays(3).build();
    StockItem okItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItem expiredItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItem expiringItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItemResponse okResponse = response(StockItemStatus.OK, null, "Pomme");
    StockItemResponse expiredResponse =
        response(StockItemStatus.EXPIRED, LocalDate.now().minusDays(1), "Lait");
    StockItemResponse expiringResponse =
        response(StockItemStatus.EXPIRING, LocalDate.now(), "Yaourt");

    when(stockItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of(okItem, expiredItem, expiringItem));
    when(stockItemMapper.toResponse(okItem, 3)).thenReturn(okResponse);
    when(stockItemMapper.toResponse(expiredItem, 3)).thenReturn(expiredResponse);
    when(stockItemMapper.toResponse(expiringItem, 3)).thenReturn(expiringResponse);

    List<StockItemResponse> result = stockItemService.listStockItems(currentUser, true);

    assertThat(result).containsExactly(expiredResponse, expiringResponse);
  }

  @Test
  void listStockItemsReturnsAllItemsWhenExpiringSoonIsFalse() {
    User currentUser = User.builder().id(UUID.randomUUID()).expirationAlertDays(3).build();
    StockItem okItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItemResponse okResponse = response(StockItemStatus.OK, null, "Pomme");

    when(stockItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of(okItem));
    when(stockItemMapper.toResponse(okItem, 3)).thenReturn(okResponse);

    List<StockItemResponse> result = stockItemService.listStockItems(currentUser, false);

    assertThat(result).containsExactly(okResponse);
  }

  @Test
  void listExpiringSoonReturnsOnlyExpiringOrExpiredSortedByExpirationDateAscending() {
    User currentUser = User.builder().id(UUID.randomUUID()).expirationAlertDays(5).build();
    StockItem okItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItem soonItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItem urgentItem = StockItem.builder().id(UUID.randomUUID()).build();
    StockItemResponse okResponse = response(StockItemStatus.OK, null, "Pomme");
    StockItemResponse soonResponse =
        response(StockItemStatus.EXPIRING, LocalDate.now().plusDays(4), "Fromage");
    StockItemResponse urgentResponse =
        response(StockItemStatus.EXPIRED, LocalDate.now().minusDays(2), "Poisson");

    when(stockItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of(okItem, soonItem, urgentItem));
    when(stockItemMapper.toResponse(okItem, 5)).thenReturn(okResponse);
    when(stockItemMapper.toResponse(soonItem, 5)).thenReturn(soonResponse);
    when(stockItemMapper.toResponse(urgentItem, 5)).thenReturn(urgentResponse);

    List<StockItemResponse> result = stockItemService.listExpiringSoon(currentUser);

    assertThat(result).containsExactly(urgentResponse, soonResponse);
  }

  @Test
  void listStockItemsUsesCurrentUserExpirationAlertDays() {
    User currentUser = User.builder().id(UUID.randomUUID()).expirationAlertDays(7).build();
    StockItem item = StockItem.builder().id(UUID.randomUUID()).build();

    when(stockItemRepository.findVisibleByUserOrderByCategoryAndProductName(currentUser))
        .thenReturn(List.of(item));
    when(stockItemMapper.toResponse(item, 7)).thenReturn(response(StockItemStatus.OK, null, "X"));

    stockItemService.listStockItems(currentUser, null);

    verify(stockItemMapper).toResponse(item, 7);
  }

  @Test
  void createStockItemReturnsCreatedResponseWhenProductIsOwnedAndNotAlreadyInStock() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(productId).name("Pomme").user(currentUser).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, BigDecimal.TEN, null, null);
    StockItemResponse expectedResponse = response(StockItemStatus.OK, null, "Pomme");

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(stockItemRepository.save(any(StockItem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(stockItemMapper.toResponse(any(StockItem.class), eq(3))).thenReturn(expectedResponse);

    StockItemResponse result = stockItemService.createStockItem(currentUser, request);

    assertThat(result).isEqualTo(expectedResponse);
    verify(stockItemRepository).save(any(StockItem.class));
    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void createStockItemThrowsConflictWhenProductAlreadyInStock() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(productId).name("Lait").user(currentUser).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, BigDecimal.TEN, null, null);

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);

    assertThatThrownBy(() -> stockItemService.createStockItem(currentUser, request))
        .isInstanceOf(StockItemAlreadyExistsException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void createStockItemThrowsNotFoundWhenProductDoesNotExist() {
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, BigDecimal.TEN, null, null);

    when(productRepository.findById(productId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockItemService.createStockItem(currentUser, request))
        .isInstanceOf(ProductNotFoundException.class);

    verifyNoInteractions(stockItemRepository);
  }

  @Test
  void createStockItemThrowsForbiddenWhenProductOwnedByAnotherUser() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(requesterId).build();
    Product product =
        Product.builder()
            .id(productId)
            .name("Secret")
            .user(User.builder().id(ownerId).build())
            .build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, BigDecimal.TEN, null, null);

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> stockItemService.createStockItem(currentUser, request))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(stockItemRepository);
  }

  @Test
  void createStockItemAddsToShoppingListWhenQuantityBelowOrEqualThreshold() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(productId).name("Riz").user(currentUser).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, new BigDecimal("2"), new BigDecimal("2"), null);

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(stockItemRepository.save(any(StockItem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(stockItemMapper.toResponse(any(StockItem.class), eq(3)))
        .thenReturn(response(StockItemStatus.LOW, null, "Riz"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    stockItemService.createStockItem(currentUser, request);

    verify(shoppingListItemRepository).save(any(ShoppingListItem.class));
  }

  @Test
  void createStockItemDoesNotAddToShoppingListWhenAlreadyPresent() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(productId).name("Riz").user(currentUser).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, new BigDecimal("1"), new BigDecimal("2"), null);

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(stockItemRepository.save(any(StockItem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(stockItemMapper.toResponse(any(StockItem.class), eq(3)))
        .thenReturn(response(StockItemStatus.LOW, null, "Riz"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);

    stockItemService.createStockItem(currentUser, request);

    verify(shoppingListItemRepository, never()).save(any());
  }

  @Test
  void createStockItemDoesNotAddToShoppingListWhenQuantityAboveThreshold() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(productId).name("Riz").user(currentUser).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, new BigDecimal("5"), new BigDecimal("2"), null);

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(stockItemRepository.save(any(StockItem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(stockItemMapper.toResponse(any(StockItem.class), eq(3)))
        .thenReturn(response(StockItemStatus.OK, null, "Riz"));

    stockItemService.createStockItem(currentUser, request);

    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void createStockItemDoesNotAddToShoppingListWhenNoThresholdProvided() {
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(productId).name("Riz").user(currentUser).build();
    CreateStockItemRequest request =
        new CreateStockItemRequest(productId, BigDecimal.ZERO, null, null);

    when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    when(stockItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);
    when(stockItemRepository.save(any(StockItem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(stockItemMapper.toResponse(any(StockItem.class), eq(3)))
        .thenReturn(response(StockItemStatus.OK, null, "Riz"));

    stockItemService.createStockItem(currentUser, request);

    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void updateQuantityUpdatesQuantityAndReturns200Payload() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Pomme").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .build();
    UpdateStockItemQuantityRequest request =
        new UpdateStockItemQuantityRequest(new BigDecimal("4"));
    StockItemResponse expectedResponse = response(StockItemStatus.OK, null, "Pomme");

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3)).thenReturn(expectedResponse);

    StockItemResponse result = stockItemService.updateQuantity(currentUser, stockItemId, request);

    assertThat(result).isEqualTo(expectedResponse);
    assertThat(stockItem.getQuantity()).isEqualByComparingTo("4");
    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void updateQuantityThrowsNotFoundWhenStockItemDoesNotExist() {
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UpdateStockItemQuantityRequest request = new UpdateStockItemQuantityRequest(BigDecimal.ONE);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockItemService.updateQuantity(currentUser, stockItemId, request))
        .isInstanceOf(StockItemNotFoundException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void updateQuantityThrowsForbiddenWhenStockItemOwnedByAnotherUser() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(requesterId).build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(User.builder().id(ownerId).build())
            .quantity(BigDecimal.TEN)
            .build();
    UpdateStockItemQuantityRequest request = new UpdateStockItemQuantityRequest(BigDecimal.ONE);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));

    assertThatThrownBy(() -> stockItemService.updateQuantity(currentUser, stockItemId, request))
        .isInstanceOf(AccessDeniedException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void updateQuantityAddsToShoppingListWhenNewQuantityBelowOrEqualThreshold() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .lowThreshold(new BigDecimal("2"))
            .build();
    UpdateStockItemQuantityRequest request =
        new UpdateStockItemQuantityRequest(new BigDecimal("2"));

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.LOW, null, "Riz"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    stockItemService.updateQuantity(currentUser, stockItemId, request);

    verify(shoppingListItemRepository).save(any(ShoppingListItem.class));
  }

  @Test
  void updateQuantityToZeroWithThresholdAddsToShoppingList() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Lait").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .lowThreshold(new BigDecimal("1"))
            .build();
    UpdateStockItemQuantityRequest request = new UpdateStockItemQuantityRequest(BigDecimal.ZERO);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.LOW, null, "Lait"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    stockItemService.updateQuantity(currentUser, stockItemId, request);

    verify(shoppingListItemRepository).save(any(ShoppingListItem.class));
  }

  @Test
  void updateQuantityDoesNotAddToShoppingListWhenAlreadyPresent() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .lowThreshold(new BigDecimal("2"))
            .build();
    UpdateStockItemQuantityRequest request = new UpdateStockItemQuantityRequest(BigDecimal.ONE);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.LOW, null, "Riz"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);

    stockItemService.updateQuantity(currentUser, stockItemId, request);

    verify(shoppingListItemRepository, never()).save(any());
  }

  @Test
  void updateQuantityDoesNotAddToShoppingListWhenAboveThreshold() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .lowThreshold(new BigDecimal("2"))
            .build();
    UpdateStockItemQuantityRequest request =
        new UpdateStockItemQuantityRequest(new BigDecimal("5"));

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.OK, null, "Riz"));

    stockItemService.updateQuantity(currentUser, stockItemId, request);

    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void updateThresholdUpdatesLowThresholdAndReturns200Payload() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Pomme").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .build();
    UpdateStockItemThresholdRequest request =
        new UpdateStockItemThresholdRequest(new BigDecimal("3"));
    StockItemResponse expectedResponse = response(StockItemStatus.OK, null, "Pomme");

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3)).thenReturn(expectedResponse);

    StockItemResponse result = stockItemService.updateThreshold(currentUser, stockItemId, request);

    assertThat(result).isEqualTo(expectedResponse);
    assertThat(stockItem.getLowThreshold()).isEqualByComparingTo("3");
    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void updateThresholdToNullRemovesThresholdAndDoesNotAddToShoppingList() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(new BigDecimal("1"))
            .lowThreshold(new BigDecimal("2"))
            .build();
    UpdateStockItemThresholdRequest request = new UpdateStockItemThresholdRequest(null);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.OK, null, "Riz"));

    stockItemService.updateThreshold(currentUser, stockItemId, request);

    assertThat(stockItem.getLowThreshold()).isNull();
    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void updateThresholdAddsToShoppingListWhenCurrentQuantityBelowOrEqualNewThreshold() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(new BigDecimal("5"))
            .build();
    UpdateStockItemThresholdRequest request =
        new UpdateStockItemThresholdRequest(new BigDecimal("10"));

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.LOW, null, "Riz"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    stockItemService.updateThreshold(currentUser, stockItemId, request);

    verify(shoppingListItemRepository).save(any(ShoppingListItem.class));
  }

  @Test
  void updateThresholdDoesNotAddToShoppingListWhenAlreadyPresent() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(new BigDecimal("5"))
            .build();
    UpdateStockItemThresholdRequest request =
        new UpdateStockItemThresholdRequest(new BigDecimal("10"));

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.LOW, null, "Riz"));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);

    stockItemService.updateThreshold(currentUser, stockItemId, request);

    verify(shoppingListItemRepository, never()).save(any());
  }

  @Test
  void updateThresholdDoesNotAddToShoppingListWhenQuantityAboveNewThreshold() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .build();
    UpdateStockItemThresholdRequest request =
        new UpdateStockItemThresholdRequest(new BigDecimal("2"));

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.OK, null, "Riz"));

    stockItemService.updateThreshold(currentUser, stockItemId, request);

    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void updateThresholdThrowsNotFoundWhenStockItemDoesNotExist() {
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UpdateStockItemThresholdRequest request = new UpdateStockItemThresholdRequest(BigDecimal.ONE);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockItemService.updateThreshold(currentUser, stockItemId, request))
        .isInstanceOf(StockItemNotFoundException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void updateThresholdThrowsForbiddenWhenStockItemOwnedByAnotherUser() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(requesterId).build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(User.builder().id(ownerId).build())
            .quantity(BigDecimal.TEN)
            .build();
    UpdateStockItemThresholdRequest request = new UpdateStockItemThresholdRequest(BigDecimal.ONE);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));

    assertThatThrownBy(() -> stockItemService.updateThreshold(currentUser, stockItemId, request))
        .isInstanceOf(AccessDeniedException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void updateExpirationSetsFutureDateAndReturns200Payload() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Pomme").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .build();
    LocalDate futureDate = LocalDate.now().plusDays(10);
    UpdateStockItemExpirationRequest request = new UpdateStockItemExpirationRequest(futureDate);
    StockItemResponse expectedResponse = response(StockItemStatus.OK, futureDate, "Pomme");

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3)).thenReturn(expectedResponse);

    StockItemResponse result = stockItemService.updateExpiration(currentUser, stockItemId, request);

    assertThat(result).isEqualTo(expectedResponse);
    assertThat(stockItem.getExpirationDate()).isEqualTo(futureDate);
  }

  @Test
  void updateExpirationAcceptsTodayAsValidDate() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Lait").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .build();
    LocalDate today = LocalDate.now();
    UpdateStockItemExpirationRequest request = new UpdateStockItemExpirationRequest(today);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.EXPIRING, today, "Lait"));

    stockItemService.updateExpiration(currentUser, stockItemId, request);

    assertThat(stockItem.getExpirationDate()).isEqualTo(today);
  }

  @Test
  void updateExpirationToNullRemovesDate() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).expirationAlertDays(3).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(currentUser)
            .product(product)
            .quantity(BigDecimal.TEN)
            .expirationDate(LocalDate.now().plusDays(2))
            .build();
    UpdateStockItemExpirationRequest request = new UpdateStockItemExpirationRequest(null);

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(stockItemRepository.save(stockItem)).thenReturn(stockItem);
    when(stockItemMapper.toResponse(stockItem, 3))
        .thenReturn(response(StockItemStatus.OK, null, "Riz"));

    stockItemService.updateExpiration(currentUser, stockItemId, request);

    assertThat(stockItem.getExpirationDate()).isNull();
  }

  @Test
  void updateExpirationThrowsNotFoundWhenStockItemDoesNotExist() {
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(UUID.randomUUID()).build();
    UpdateStockItemExpirationRequest request =
        new UpdateStockItemExpirationRequest(LocalDate.now());

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockItemService.updateExpiration(currentUser, stockItemId, request))
        .isInstanceOf(StockItemNotFoundException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void updateExpirationThrowsForbiddenWhenStockItemOwnedByAnotherUser() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(requesterId).build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(User.builder().id(ownerId).build())
            .quantity(BigDecimal.TEN)
            .build();
    UpdateStockItemExpirationRequest request =
        new UpdateStockItemExpirationRequest(LocalDate.now());

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    assertThatThrownBy(() -> stockItemService.updateExpiration(currentUser, stockItemId, request))
        .isInstanceOf(AccessDeniedException.class);

    verify(stockItemRepository, never()).save(any());
  }

  @Test
  void deleteStockItemRemovesInstanceWhenOwnedByCurrentUser() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();
    StockItem stockItem =
        StockItem.builder().id(stockItemId).user(currentUser).quantity(BigDecimal.TEN).build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));

    stockItemService.deleteStockItem(currentUser, stockItemId);

    verify(stockItemRepository).delete(stockItem);
  }

  @Test
  void deleteStockItemThrowsNotFoundWhenStockItemDoesNotExist() {
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(UUID.randomUUID()).build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockItemService.deleteStockItem(currentUser, stockItemId))
        .isInstanceOf(StockItemNotFoundException.class);

    verify(stockItemRepository, never()).delete(any(StockItem.class));
  }

  @Test
  void deleteStockItemThrowsForbiddenWhenStockItemOwnedByAnotherUser() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(requesterId).build();
    StockItem stockItem =
        StockItem.builder()
            .id(stockItemId)
            .user(User.builder().id(ownerId).build())
            .quantity(BigDecimal.TEN)
            .build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));

    assertThatThrownBy(() -> stockItemService.deleteStockItem(currentUser, stockItemId))
        .isInstanceOf(AccessDeniedException.class);

    verify(stockItemRepository, never()).delete(any(StockItem.class));
  }

  @Test
  void addToShoppingListManuallyCreatesEntryWithAddedAutomaticallyFalse() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Pomme").build();
    StockItem stockItem =
        StockItem.builder().id(stockItemId).user(currentUser).product(product).build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(false);

    stockItemService.addToShoppingListManually(currentUser, stockItemId);

    ArgumentCaptor<ShoppingListItem> captor = ArgumentCaptor.forClass(ShoppingListItem.class);
    verify(shoppingListItemRepository).save(captor.capture());
    assertThat(captor.getValue().isAddedAutomatically()).isFalse();
    assertThat(captor.getValue().getProduct()).isEqualTo(product);
  }

  @Test
  void addToShoppingListManuallyThrowsConflictWhenAlreadyPresent() {
    UUID userId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(userId).build();
    Product product = Product.builder().id(UUID.randomUUID()).name("Riz").build();
    StockItem stockItem =
        StockItem.builder().id(stockItemId).user(currentUser).product(product).build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
    when(shoppingListItemRepository.existsByUserAndProduct(currentUser, product)).thenReturn(true);

    assertThatThrownBy(() -> stockItemService.addToShoppingListManually(currentUser, stockItemId))
        .isInstanceOf(ShoppingListItemAlreadyExistsException.class);

    verify(shoppingListItemRepository, never()).save(any());
  }

  @Test
  void addToShoppingListManuallyThrowsNotFoundWhenStockItemDoesNotExist() {
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(UUID.randomUUID()).build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockItemService.addToShoppingListManually(currentUser, stockItemId))
        .isInstanceOf(StockItemNotFoundException.class);

    verifyNoInteractions(shoppingListItemRepository);
  }

  @Test
  void addToShoppingListManuallyThrowsForbiddenWhenStockItemOwnedByAnotherUser() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    User currentUser = User.builder().id(requesterId).build();
    StockItem stockItem =
        StockItem.builder().id(stockItemId).user(User.builder().id(ownerId).build()).build();

    when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));

    assertThatThrownBy(() -> stockItemService.addToShoppingListManually(currentUser, stockItemId))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(shoppingListItemRepository);
  }
}
