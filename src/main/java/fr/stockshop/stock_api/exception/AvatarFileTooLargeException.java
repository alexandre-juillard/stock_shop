package fr.stockshop.stock_api.exception;

public class AvatarFileTooLargeException extends ApiException {

  public AvatarFileTooLargeException() {
    super(413, "error.avatar.tooLarge");
  }
}
