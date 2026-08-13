package fr.stockshop.stock_api.common.storage;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ProductPhotoStorageService {

  String savePhoto(UUID productId, MultipartFile file);

  void deletePhoto(String photoUrl);
}
