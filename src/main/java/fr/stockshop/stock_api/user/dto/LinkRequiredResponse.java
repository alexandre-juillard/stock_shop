package fr.stockshop.stock_api.user.dto;

/**
 * Réponse renvoyée lorsqu'un callback Google correspond par email à un compte local actif sans
 * décision de liaison déjà enregistrée : aucun jeton n'est émis tant que l'utilisateur n'a pas
 * confirmé son choix via POST /api/auth/oauth2/link-decision.
 */
public record LinkRequiredResponse(String status, String linkContext) {

  public static LinkRequiredResponse of(String linkContext) {
    return new LinkRequiredResponse("LINK_REQUIRED", linkContext);
  }
}
