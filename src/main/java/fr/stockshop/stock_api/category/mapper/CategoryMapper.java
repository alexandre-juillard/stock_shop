package fr.stockshop.stock_api.category.mapper;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.category.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

  public CategoryResponse toResponse(Category category) {
    return new CategoryResponse(category.getId(), category.getName(), category.getColor());
  }
}
