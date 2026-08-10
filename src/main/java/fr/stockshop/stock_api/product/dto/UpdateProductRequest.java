package fr.stockshop.stock_api.product.dto;

import java.util.UUID;

/** Mise à jour partielle : name et/ou categoryId sont optionnels. */
public record UpdateProductRequest(String name, UUID categoryId) {}
