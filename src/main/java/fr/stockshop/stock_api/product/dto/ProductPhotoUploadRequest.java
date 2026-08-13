package fr.stockshop.stock_api.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProductPhotoUploadRequest(
    @Schema(description = "Image du produit", type = "string", format = "binary") byte[] file) {}

