package fr.stockshop.stock_api.user.storage;

import fr.stockshop.stock_api.common.storage.AvatarStorageService;
import fr.stockshop.stock_api.exception.AvatarFileTooLargeException;
import fr.stockshop.stock_api.exception.AvatarStorageException;
import fr.stockshop.stock_api.exception.UnsupportedAvatarFormatException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LocalAvatarStorageService implements AvatarStorageService {

  private static final String PUBLIC_PREFIX = "/uploads/avatars/";
  private static final Map<String, String> MIME_TYPES_VERS_EXTENSION =
      Map.of(
          "image/jpeg", "jpg",
          "image/jpg", "jpg",
          "image/png", "png",
          "image/webp", "webp");
  private static final Map<String, String> EXTENSIONS_CANONIQUES =
      Map.of(
          "jpg", "jpg",
          "jpeg", "jpg",
          "png", "png",
          "webp", "webp");

  @Value("${app.storage.avatar-directory:uploads/avatars}")
  private String repertoireAvatars;

  @Value("${app.storage.avatar-max-size:5MB}")
  private DataSize tailleMaxAvatar;

  @Override
  public String saveAvatar(UUID utilisateurId, MultipartFile fichier) {
    verifierTaille(fichier);
    String extension = determinerExtension(fichier);
    Path repertoire = cheminRepertoire();
    Path fichierCible = repertoire.resolve(utilisateurId + "." + extension);

    try {
      Files.createDirectories(repertoire);
      try (InputStream inputStream = fichier.getInputStream()) {
        Files.copy(inputStream, fichierCible, StandardCopyOption.REPLACE_EXISTING);
      }
      nettoyerAnciensFichiers(utilisateurId, fichierCible);
      return PUBLIC_PREFIX + fichierCible.getFileName();
    } catch (IOException ex) {
      throw new AvatarStorageException(ex);
    }
  }

  @Override
  public void deleteAvatar(String avatarUrl) {
    if (avatarUrl == null || avatarUrl.isBlank()) {
      return;
    }

    String nomFichier = extraireNomFichier(avatarUrl);
    if (nomFichier == null) {
      return;
    }

    try {
      Files.deleteIfExists(cheminRepertoire().resolve(nomFichier));
    } catch (IOException ex) {
      throw new AvatarStorageException(ex);
    }
  }

  private void verifierTaille(MultipartFile fichier) {
    if (fichier.getSize() > tailleMaxAvatar.toBytes()) {
      throw new AvatarFileTooLargeException();
    }
  }

  private String determinerExtension(MultipartFile fichier) {
    String typeMime = fichier.getContentType();
    if (typeMime != null
        && MIME_TYPES_VERS_EXTENSION.containsKey(typeMime.toLowerCase(Locale.ROOT))) {
      return MIME_TYPES_VERS_EXTENSION.get(typeMime.toLowerCase(Locale.ROOT));
    }

    String nomOriginal = fichier.getOriginalFilename();
    if (nomOriginal != null) {
      int indexPoint = nomOriginal.lastIndexOf('.');
      if (indexPoint >= 0 && indexPoint < nomOriginal.length() - 1) {
        String extension = nomOriginal.substring(indexPoint + 1).toLowerCase(Locale.ROOT);
        if (EXTENSIONS_CANONIQUES.containsKey(extension)) {
          return EXTENSIONS_CANONIQUES.get(extension);
        }
      }
    }

    throw new UnsupportedAvatarFormatException();
  }

  private Path cheminRepertoire() {
    return Path.of(repertoireAvatars).toAbsolutePath().normalize();
  }

  private void nettoyerAnciensFichiers(UUID utilisateurId, Path fichierCible) throws IOException {
    String prefixe = utilisateurId + ".";
    try (DirectoryStream<Path> fichiers =
        Files.newDirectoryStream(cheminRepertoire(), prefixe + "*")) {
      for (Path fichierExistant : fichiers) {
        if (!Objects.equals(fichierExistant.getFileName(), fichierCible.getFileName())) {
          Files.deleteIfExists(fichierExistant);
        }
      }
    }
  }

  private String extraireNomFichier(String avatarUrl) {
    int indexDernierSlash = avatarUrl.lastIndexOf('/');
    if (indexDernierSlash < 0 || indexDernierSlash == avatarUrl.length() - 1) {
      return null;
    }
    return avatarUrl.substring(indexDernierSlash + 1);
  }
}
