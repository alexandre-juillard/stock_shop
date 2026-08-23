package fr.stockshop.stock_api.notification.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/** Plateforme d'origine d'un token push (voir table {@code push_tokens}). */
public enum PushPlatform {
  ANDROID,
  IOS;

  @JsonCreator
  public static PushPlatform fromValue(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    return PushPlatform.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
  }
}
