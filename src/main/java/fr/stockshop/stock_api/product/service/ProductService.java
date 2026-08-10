package fr.stockshop.stock_api.product.service;

import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.category.repository.CategoryRepository;
import fr.stockshop.stock_api.exception.CategoryNotFoundException;
import fr.stockshop.stock_api.exception.ProductNameAlreadyExistsException;
import fr.stockshop.stock_api.exception.QuantityTypeMismatchException;
import fr.stockshop.stock_api.exception.QuantityTypeNotFoundException;
import fr.stockshop.stock_api.exception.QuantityUnitNotFoundException;
import fr.stockshop.stock_api.product.dto.CreateProductRequest;
import fr.stockshop.stock_api.product.dto.ProductResponse;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.mapper.ProductMapper;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityType;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.repository.QuantityTypeRepository;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import fr.stockshop.stock_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final QuantityTypeRepository quantityTypeRepository;
  private final QuantityUnitRepository quantityUnitRepository;
  private final ProductMapper productMapper;

  @Transactional
  public ProductResponse createProduct(User currentUser, CreateProductRequest request) {
    String productName = request.name();

    if (productRepository.existsByUserAndName(currentUser, productName)) {
      throw new ProductNameAlreadyExistsException(productName);
    }

    Category category =
        categoryRepository
            .findByIdAndUser(request.categoryId(), currentUser)
            .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

    QuantityType quantityType =
        quantityTypeRepository
            .findById(request.quantityTypeId())
            .orElseThrow(
                () -> new QuantityTypeNotFoundException(request.quantityTypeId().toString()));

    QuantityUnit baseUnit =
        quantityUnitRepository
            .findById(request.baseUnitId())
            .orElseThrow(() -> new QuantityUnitNotFoundException(request.baseUnitId()));

    if (!baseUnit.getQuantityType().getId().equals(quantityType.getId())) {
      throw new QuantityTypeMismatchException(quantityType.getId(), baseUnit.getId());
    }

    Product product =
        Product.builder()
            .user(currentUser)
            .category(category)
            .name(productName)
            .quantityType(quantityType)
            .baseUnit(baseUnit)
            .visible(true)
            .build();

    try {
      return productMapper.toResponse(productRepository.save(product));
    } catch (DataIntegrityViolationException ex) {
      throw new ProductNameAlreadyExistsException(productName);
    }
  }
}
