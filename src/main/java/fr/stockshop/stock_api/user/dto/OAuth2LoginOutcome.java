package fr.stockshop.stock_api.user.dto;

/**
 * Résultat d'une tentative de connexion OAuth2 : soit des jetons émis directement, soit une demande
 * de confirmation de liaison de compte (voir {@link LinkRequiredResponse}). Exactement un seul des
 * deux champs est renseigné.
 */
public record OAuth2LoginOutcome(LoginResponse tokens, LinkRequiredResponse linkRequired) {

  public static OAuth2LoginOutcome loggedIn(LoginResponse tokens) {
    return new OAuth2LoginOutcome(tokens, null);
  }

  public static OAuth2LoginOutcome linkRequired(String linkContext) {
    return new OAuth2LoginOutcome(null, LinkRequiredResponse.of(linkContext));
  }

  public boolean requiresLink() {
    return linkRequired != null;
  }
}
