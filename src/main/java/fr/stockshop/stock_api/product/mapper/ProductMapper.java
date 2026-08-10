package fr.stockshop.stock_api.product.mapper;

import fr.stockshop.stock_api.category.mapper.CategoryMapper;
import fr.stockshop.stock_api.product.dto.ProductResponse;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.quantity.mapper.QuantityReferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductMapper {

  private final CategoryMapper categoryMapper;
  private final QuantityReferenceMapper quantityReferenceMapper;

  public ProductResponse toResponse(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        categoryMapper.toResponse(product.getCategory()),
        quantityReferenceMapper.toTypeResponse(product.getQuantityType()),
        quantityReferenceMapper.toUnitResponse(product.getBaseUnit()),
        product.isVisible());
  }
}
