package fr.stockshop.stock_api.product.storage;

import fr.stockshop.stock_api.common.storage.ProductPhotoStorageService;
import fr.stockshop.stock_api.exception.ProductPhotoFileTooLargeException;
import fr.stockshop.stock_api.exception.ProductPhotoStorageException;
import fr.stockshop.stock_api.exception.UnsupportedProductPhotoFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalProductPhotoStorageService implements ProductPhotoStorageService {

  private static final String PUBLIC_PREFIX = "/uploads/products/";
  private static final Map<String, String> MIME_TYPES_TO_EXTENSION =
      Map.of(
          "image/jpeg", "jpg",
          "image/jpg", "jpg",
          "image/png", "png",
          "image/webp", "webp");
  private static final Map<String, String> CANONICAL_EXTENSIONS =
      Map.of(
          "jpg", "jpg",
          "jpeg", "jpg",
          "png", "png",
          "webp", "webp");

  @Value("${app.storage.product-photo-directory:uploads/products}")
  private String photosDirectory;

  @Value("${app.storage.product-photo-max-size:5MB}")
  private DataSize maxPhotoSize;

  @Override
  public String savePhoto(UUID productId, MultipartFile file) {
    validateSize(file);
    String extension = resolveExtension(file);
    Path directory = resolveDirectory();
    Path targetFile = directory.resolve(productId + "." + extension);

    try {
      Files.createDirectories(directory);
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
      }
      cleanupOldFiles(productId, targetFile);
      return PUBLIC_PREFIX + targetFile.getFileName();
    } catch (IOException ex) {
      throw new ProductPhotoStorageException(ex);
    }
  }

  @Override
  public void deletePhoto(String photoUrl) {
    if (photoUrl == null || photoUrl.isBlank()) {
      return;
    }

    String fileName = extractFileName(photoUrl);
    if (fileName == null) {
      return;
    }

    try {
      Files.deleteIfExists(resolveDirectory().resolve(fileName));
    } catch (IOException ex) {
      throw new ProductPhotoStorageException(ex);
    }
  }

  private void validateSize(MultipartFile file) {
    if (file.getSize() > maxPhotoSize.toBytes()) {
      throw new ProductPhotoFileTooLargeException();
    }
  }

  private String resolveExtension(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType != null
        && MIME_TYPES_TO_EXTENSION.containsKey(contentType.toLowerCase(Locale.ROOT))) {
      return MIME_TYPES_TO_EXTENSION.get(contentType.toLowerCase(Locale.ROOT));
    }

    String originalName = file.getOriginalFilename();
    if (originalName != null) {
      int dotIndex = originalName.lastIndexOf('.');
      if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
        String extension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (CANONICAL_EXTENSIONS.containsKey(extension)) {
          return CANONICAL_EXTENSIONS.get(extension);
        }
      }
    }

    throw new UnsupportedProductPhotoFormatException();
  }

  private Path resolveDirectory() {
    return Path.of(photosDirectory).toAbsolutePath().normalize();
  }

  private void cleanupOldFiles(UUID productId, Path targetFile) throws IOException {
    String prefix = productId + ".";
    try (DirectoryStream<Path> existingFiles =
        Files.newDirectoryStream(resolveDirectory(), prefix + "*")) {
      for (Path existingFile : existingFiles) {
        if (!Objects.equals(existingFile.getFileName(), targetFile.getFileName())) {
          Files.deleteIfExists(existingFile);
        }
      }
    }
  }

  private String extractFileName(String photoUrl) {
    int lastSlashIndex = photoUrl.lastIndexOf('/');
    if (lastSlashIndex < 0 || lastSlashIndex == photoUrl.length() - 1) {
      return null;
    }
    return photoUrl.substring(lastSlashIndex + 1);
  }
}
