package fr.stockshop.stock_api.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRecipeRequest(
    @NotBlank(message = "{validation.recipe.name.required}")
        @Size(max = 200, message = "{validation.recipe.name.max}")
        String name,
    @Valid List<CreateRecipeIngredientRequest> ingredients) {

  public CreateRecipeRequest {
    ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
  }

  @Override
  public List<CreateRecipeIngredientRequest> ingredients() {
    return List.copyOf(ingredients);
  }
}
