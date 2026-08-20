package fr.stockshop.stock_api.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import fr.stockshop.stock_api.stock.dto.StockItemProductSummary;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import fr.stockshop.stock_api.stock.mapper.StockItemMapper;
import fr.stockshop.stock_api.stock.repository.StockItemRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockItemServiceTest {

  @Mock private StockItemRepository stockItemRepository;
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
}
