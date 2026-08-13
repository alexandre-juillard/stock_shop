package fr.stockshop.stock_api.product.service;

import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.category.repository.CategoryRepository;
import fr.stockshop.stock_api.common.storage.ProductPhotoStorageService;
import fr.stockshop.stock_api.exception.CategoryNotFoundException;
import fr.stockshop.stock_api.exception.ProductNameAlreadyExistsException;
import fr.stockshop.stock_api.exception.ProductNotFoundException;
import fr.stockshop.stock_api.exception.QuantityTypeMismatchException;
import fr.stockshop.stock_api.exception.QuantityTypeNotFoundException;
import fr.stockshop.stock_api.exception.QuantityUnitNotFoundException;
import fr.stockshop.stock_api.product.dto.CreateProductRequest;
import fr.stockshop.stock_api.product.dto.ProductPhotoResponse;
import fr.stockshop.stock_api.product.dto.ProductResponse;
import fr.stockshop.stock_api.product.dto.UpdateProductRequest;
import fr.stockshop.stock_api.product.dto.UpdateProductVisibilityRequest;
import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.product.mapper.ProductMapper;
import fr.stockshop.stock_api.product.repository.ProductRepository;
import fr.stockshop.stock_api.quantity.entity.QuantityType;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.quantity.repository.QuantityTypeRepository;
import fr.stockshop.stock_api.quantity.repository.QuantityUnitRepository;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final QuantityTypeRepository quantityTypeRepository;
  private final QuantityUnitRepository quantityUnitRepository;
  private final ProductMapper productMapper;
  private final ProductPhotoStorageService productPhotoStorageService;

  @Transactional(readOnly = true)
  public List<ProductResponse> listProducts(User currentUser, UUID categoryId, Boolean visible) {
    List<Product> products;
    if (categoryId != null && visible != null) {
      products =
          productRepository.findByUserAndCategory_IdAndVisibleOrderByNameAsc(
              currentUser, categoryId, visible);
    } else if (categoryId != null) {
      products = productRepository.findByUserAndCategory_IdOrderByNameAsc(currentUser, categoryId);
    } else if (visible != null) {
      products = productRepository.findByUserAndVisibleOrderByNameAsc(currentUser, visible);
    } else {
      products = productRepository.findByUserOrderByNameAsc(currentUser);
    }
    return products.stream().map(productMapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse getProduct(User currentUser, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);
    return productMapper.toResponse(product);
  }

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

  @Transactional
  public ProductResponse updateProduct(
      User currentUser, UUID productId, UpdateProductRequest request) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);

    if (request.name() != null) {
      String newName = request.name();
      if (!newName.equals(product.getName())
          && productRepository.existsByUserAndNameAndIdNot(currentUser, newName, productId)) {
        throw new ProductNameAlreadyExistsException(newName);
      }
      product.setName(newName);
    }

    if (request.categoryId() != null) {
      Category category =
          categoryRepository
              .findByIdAndUser(request.categoryId(), currentUser)
              .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
      product.setCategory(category);
    }

    try {
      return productMapper.toResponse(productRepository.save(product));
    } catch (DataIntegrityViolationException ex) {
      throw new ProductNameAlreadyExistsException(product.getName());
    }
  }

  @Transactional
  public void deleteProduct(User currentUser, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);
    productPhotoStorageService.deletePhoto(product.getPhotoUrl());
    productRepository.delete(product);
  }

  @Transactional
  public ProductPhotoResponse uploadPhoto(User currentUser, UUID productId, MultipartFile file) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);

    String previousPhotoUrl = product.getPhotoUrl();
    String newPhotoUrl = productPhotoStorageService.savePhoto(productId, file);

    if (previousPhotoUrl != null && !previousPhotoUrl.equals(newPhotoUrl)) {
      productPhotoStorageService.deletePhoto(previousPhotoUrl);
    }

    product.setPhotoUrl(newPhotoUrl);
    productRepository.save(product);
    return new ProductPhotoResponse(newPhotoUrl);
  }

  @Transactional
  public void deletePhoto(User currentUser, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);

    productPhotoStorageService.deletePhoto(product.getPhotoUrl());
    product.setPhotoUrl(null);
    productRepository.save(product);
  }

  @Transactional
  public ProductResponse updateVisibility(
      User currentUser, UUID productId, UpdateProductVisibilityRequest request) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    assertOwnership(product, currentUser);
    product.setVisible(request.isVisible());
    return productMapper.toResponse(productRepository.save(product));
  }

  private void assertOwnership(Product product, User currentUser) {
    if (!product.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Product does not belong to current user");
    }
  }
}
