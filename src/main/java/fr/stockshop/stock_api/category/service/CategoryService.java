package fr.stockshop.stock_api.category.service;

import fr.stockshop.stock_api.category.dto.CategoryResponse;
import fr.stockshop.stock_api.category.dto.CreateCategoryRequest;
import fr.stockshop.stock_api.category.dto.UpdateCategoryRequest;
import fr.stockshop.stock_api.category.entity.Category;
import fr.stockshop.stock_api.category.mapper.CategoryMapper;
import fr.stockshop.stock_api.category.repository.CategoryRepository;
import fr.stockshop.stock_api.exception.CategoryNameAlreadyExistsException;
import fr.stockshop.stock_api.exception.CategoryNotFoundException;
import fr.stockshop.stock_api.user.entity.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final CategoryMapper categoryMapper;

  @Transactional(readOnly = true)
  public List<CategoryResponse> listCategories(User currentUser) {
    return categoryRepository.findByUserOrderByNameAsc(currentUser).stream()
        .map(categoryMapper::toResponse)
        .toList();
  }

  @Transactional
  public CategoryResponse createCategory(User currentUser, CreateCategoryRequest request) {
    String nomNormalise = request.name().trim();

    if (categoryRepository.existsByUserAndNameIgnoreCase(currentUser, nomNormalise)) {
      throw new CategoryNameAlreadyExistsException(nomNormalise);
    }

    Category category =
        Category.builder().user(currentUser).name(nomNormalise).color(request.color()).build();

    try {
      return categoryMapper.toResponse(categoryRepository.save(category));
    } catch (DataIntegrityViolationException ex) {
      throw new CategoryNameAlreadyExistsException(nomNormalise);
    }
  }

  @Transactional
  public CategoryResponse updateCategory(
      User currentUser, UUID categoryId, UpdateCategoryRequest request) {
    Category category = resolveOwnedCategory(currentUser, categoryId);
    String nomNormalise = request.name().trim();

    if (categoryRepository.existsByUserAndNameIgnoreCaseAndIdNot(
        currentUser, nomNormalise, categoryId)) {
      throw new CategoryNameAlreadyExistsException(nomNormalise);
    }

    category.setName(nomNormalise);
    category.setColor(request.color());

    try {
      return categoryMapper.toResponse(categoryRepository.save(category));
    } catch (DataIntegrityViolationException ex) {
      throw new CategoryNameAlreadyExistsException(nomNormalise);
    }
  }

  @Transactional
  public void deleteCategory(User currentUser, UUID categoryId) {
    Category category = resolveOwnedCategory(currentUser, categoryId);
    categoryRepository.deleteRecipesLinkedToCategoryProducts(currentUser.getId(), categoryId);
    categoryRepository.delete(category);
  }

  private Category resolveOwnedCategory(User currentUser, UUID categoryId) {
    Category category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new CategoryNotFoundException(categoryId));

    if (!category.getUser().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Category does not belong to current user");
    }

    return category;
  }
}
