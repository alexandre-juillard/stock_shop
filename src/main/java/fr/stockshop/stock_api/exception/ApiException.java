package fr.stockshop.stock_api.exception;

/** Exception métier de base : porte le statut HTTP et la clé i18n du message à afficher. */
public class ApiException extends RuntimeException {

  private final int status;
  private final String messageCode;
  private final Object[] args;

  public ApiException(int status, String messageCode, Object... args) {
    super(messageCode);
    this.status = status;
    this.messageCode = messageCode;
    this.args = args == null ? new Object[0] : args.clone();
  }

  public int getStatus() {
    return status;
  }

  public String getMessageCode() {
    return messageCode;
  }

  public Object[] getArgs() {
    return args.clone();
  }
}
