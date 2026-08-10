package fr.stockshop.stock_api.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {

  public CategoryNotFoundException(UUID categoryId) {
    super(HttpStatus.NOT_FOUND.value(), "error.category.notFound", categoryId);
  }
}
