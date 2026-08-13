package fr.stockshop.stock_api.exception;

public class ProductPhotoFileTooLargeException extends ApiException {

  public ProductPhotoFileTooLargeException() {
    super(413, "error.product.photo.tooLarge");
  }
}
