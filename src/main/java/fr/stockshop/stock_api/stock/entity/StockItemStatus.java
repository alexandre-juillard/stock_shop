package fr.stockshop.stock_api.stock.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statut calculé d'un {@link StockItem}, dérivé de la quantité, du seuil bas et de la date
 * d'expiration au moment de la lecture (non persisté).
 *
 * <p>Priorité en cas de cumul : {@code EXPIRED > EXPIRING > LOW > OK}.
 */
public enum StockItemStatus {
  OK("ok"),
  LOW("low"),
  EXPIRING("expiring"),
  EXPIRED("expired");

  private final String value;

  StockItemStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
