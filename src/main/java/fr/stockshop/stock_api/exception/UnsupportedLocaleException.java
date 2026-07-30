package fr.stockshop.stock_api.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

public class UnsupportedLocaleException extends ApiException {

  public UnsupportedLocaleException(String locale, List<String> supportedLocales) {
    super(
        HttpStatus.BAD_REQUEST.value(),
        "error.locale.unsupported",
        locale,
        String.join(", ", supportedLocales));
  }
}
