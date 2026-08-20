package fr.stockshop.stock_api.stock.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.category.mapper.CategoryMapper;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.quantity.dto.QuantityUnitResponse;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.mapper.QuantityReferenceMapper;
import fr.stockshop.stock_api.stock.dto.StockItemResponse;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.stock.entity.StockItemStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockItemMapperTest {

  private static final int EXPIRATION_ALERT_DAYS = 3;

  @Mock private CategoryMapper categoryMapper;
  @Mock private QuantityReferenceMapper quantityReferenceMapper;

  private StockItemMapper stockItemMapper;
  private Category category;
  private QuantityUnit baseUnit;
  private Product product;

  @BeforeEach
  void setUp() {
    stockItemMapper = new StockItemMapper(categoryMapper, quantityReferenceMapper);
    category = Category.builder().id(UUID.randomUUID()).name("Fruits").color("#112233").build();
    baseUnit = QuantityUnit.builder().id(UUID.randomUUID()).code("kg").build();
    product =
        Product.builder()
            .id(UUID.randomUUID())
            .name("Pomme")
            .category(category)
            .baseUnit(baseUnit)
            .build();
  }

  private StockItem stockItem(
      BigDecimal quantity, BigDecimal lowThreshold, LocalDate expirationDate) {
    return StockItem.builder()
        .id(UUID.randomUUID())
        .product(product)
        .quantity(quantity)
        .lowThreshold(lowThreshold)
        .expirationDate(expirationDate)
        .build();
  }

  @Test
  void toResponseReturnsOkWhenNoThresholdAndNoExpiration() {
    StockItem item = stockItem(BigDecimal.TEN, null, null);

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.OK);
    assertThat(response.needsQuantityUpdate()).isFalse();
  }

  @Test
  void toResponseReturnsOkWhenQuantityAboveThreshold() {
    StockItem item = stockItem(new BigDecimal("5"), new BigDecimal("2"), null);

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.OK);
  }

  @Test
  void toResponseReturnsLowWhenQuantityEqualsThreshold() {
    StockItem item = stockItem(new BigDecimal("2"), new BigDecimal("2"), null);

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.LOW);
  }

  @Test
  void toResponseReturnsLowWhenQuantityBelowThreshold() {
    StockItem item = stockItem(new BigDecimal("1"), new BigDecimal("2"), null);

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.LOW);
  }

  @Test
  void toResponseReturnsExpiredWhenExpirationDateIsInThePast() {
    StockItem item = stockItem(BigDecimal.TEN, null, LocalDate.now().minusDays(1));

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.EXPIRED);
    assertThat(response.needsQuantityUpdate()).isTrue();
  }

  @Test
  void toResponseReturnsExpiringWhenExpirationDateIsToday() {
    StockItem item = stockItem(BigDecimal.TEN, null, LocalDate.now());

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.EXPIRING);
    assertThat(response.needsQuantityUpdate()).isFalse();
  }

  @Test
  void toResponseReturnsExpiringAtUpperBoundaryOfAlertDelay() {
    StockItem item =
        stockItem(BigDecimal.TEN, null, LocalDate.now().plusDays(EXPIRATION_ALERT_DAYS));

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.EXPIRING);
  }

  @Test
  void toResponseReturnsOkWhenExpirationDateIsJustBeyondAlertDelay() {
    StockItem item =
        stockItem(BigDecimal.TEN, null, LocalDate.now().plusDays(EXPIRATION_ALERT_DAYS + 1));

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.OK);
  }

  @Test
  void toResponsePrioritizesExpiredOverLowThreshold() {
    StockItem item =
        stockItem(new BigDecimal("1"), new BigDecimal("5"), LocalDate.now().minusDays(1));

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.EXPIRED);
  }

  @Test
  void toResponsePrioritizesExpiringOverLowThreshold() {
    StockItem item = stockItem(new BigDecimal("1"), new BigDecimal("5"), LocalDate.now());

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.status()).isEqualTo(StockItemStatus.EXPIRING);
  }

  @Test
  void toResponseMapsNestedProductSummary() {
    StockItem item = stockItem(BigDecimal.TEN, null, null);
    CategoryResponse categoryResponse =
        new CategoryResponse(category.getId(), category.getName(), category.getColor());
    QuantityUnitResponse unitResponse =
        new QuantityUnitResponse(baseUnit.getId(), "kg", "Kilogramme", BigDecimal.ONE, true);
    when(categoryMapper.toResponse(category)).thenReturn(categoryResponse);
    when(quantityReferenceMapper.toUnitResponse(baseUnit)).thenReturn(unitResponse);

    StockItemResponse response = stockItemMapper.toResponse(item, EXPIRATION_ALERT_DAYS);

    assertThat(response.product().id()).isEqualTo(product.getId());
    assertThat(response.product().name()).isEqualTo("Pomme");
    assertThat(response.product().category()).isEqualTo(categoryResponse);
    assertThat(response.product().baseUnit()).isEqualTo(unitResponse);
  }
}
