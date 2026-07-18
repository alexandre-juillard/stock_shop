package fr.stockshop.stock_api.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Ajoute le support du bouton "Authorize" (Bearer JWT) dans Swagger UI.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME_NAME = "bearerAuth";

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Stock API")
						.description("API de gestion de stock (produits, catégories, recettes, utilisateurs)")
						.version("v0.0.1"))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME,
						new SecurityScheme()
								.name(BEARER_SCHEME_NAME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}

