package fr.stockshop.stock_api.common.storage;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {

  String saveAvatar(UUID utilisateurId, MultipartFile fichier);

  void deleteAvatar(String avatarUrl);
}
