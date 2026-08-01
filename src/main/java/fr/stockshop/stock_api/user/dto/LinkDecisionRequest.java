package fr.stockshop.stock_api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Corps de requête pour POST /api/auth/oauth2/link-decision. */
public record LinkDecisionRequest(
    @NotBlank(message = "{validation.linkContext.required}") String linkContext,
    @NotNull(message = "{validation.decision.required}") LinkDecision decision) {}
