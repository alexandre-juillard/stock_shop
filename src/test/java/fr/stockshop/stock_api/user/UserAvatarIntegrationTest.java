package fr.stockshop.stock_api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.stockshop.stock_api.TestcontainersConfiguration;
import fr.stockshop.stock_api.mail.EmailService;
import fr.stockshop.stock_api.user.entity.User;
import fr.stockshop.stock_api.user.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserAvatarIntegrationTest {

  private static final Path REPERTOIRE_AVATARS_TEST;

  static {
    try {
      REPERTOIRE_AVATARS_TEST = Files.createTempDirectory("stock-api-avatars-");
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @DynamicPropertySource
  static void registerAvatarProperties(DynamicPropertyRegistry registry) {
    registry.add("app.storage.avatar-directory", REPERTOIRE_AVATARS_TEST::toString);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @MockitoBean private EmailService emailService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void uploadAvatarStoresFileAndExposesItPublicly() throws Exception {
    String email = "avatar-upload-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    MockMultipartFile fichier =
        new MockMultipartFile("file", "avatar.png", "image/png", imageBytes);

    String body =
        mockMvc
            .perform(
                multipart("/api/users/me/avatar")
                    .file(fichier)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.avatarUrl")
                    .value(org.hamcrest.Matchers.startsWith("/uploads/avatars/")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String avatarUrl = objectMapper.readTree(body).get("avatarUrl").asText();
    String nomFichier = avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1);
    Path fichierAvatar = REPERTOIRE_AVATARS_TEST.resolve(nomFichier);
    assertThat(Files.exists(fichierAvatar)).isTrue();
    assertThat(Files.readAllBytes(fichierAvatar)).containsExactly(imageBytes);

    mockMvc
        .perform(get(avatarUrl))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsByteArray())
                    .containsExactly(imageBytes));
  }

  @Test
  void deleteAvatarRemovesStoredFileAndClearsProfile() throws Exception {
    String email = "avatar-delete-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    MockMultipartFile fichier =
        new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[] {1, 2, 3, 4});

    String body =
        mockMvc
            .perform(
                multipart("/api/users/me/avatar")
                    .file(fichier)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String avatarUrl = objectMapper.readTree(body).get("avatarUrl").asText();
    String nomFichier = avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1);
    Path fichierAvatar = REPERTOIRE_AVATARS_TEST.resolve(nomFichier);
    assertThat(Files.exists(fichierAvatar)).isTrue();

    mockMvc
        .perform(delete("/api/users/me/avatar").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getAvatarUrl()).isNull();
    assertThat(Files.exists(fichierAvatar)).isFalse();

    mockMvc.perform(get(avatarUrl)).andExpect(status().isNotFound());
  }

  @Test
  void uploadAvatarWithUnsupportedFormatReturnsBadRequest() throws Exception {
    String email = "avatar-invalid-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    MockMultipartFile fichier =
        new MockMultipartFile("file", "document.pdf", "application/pdf", new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/api/users/me/avatar")
                .file(fichier)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("Format d'avatar non supporté : utilisez une image JPG, PNG ou WebP"));
  }

  @Test
  void uploadAvatarBeyondConfiguredLimitReturnsPayloadTooLarge() throws Exception {
    String email = "avatar-large-" + UUID.randomUUID() + "@test.fr";
    String accessToken = registerActivateAndLogin(email);
    byte[] bigFile = new byte[5 * 1024 * 1024 + 1];
    MockMultipartFile fichier = new MockMultipartFile("file", "avatar.png", "image/png", bigFile);

    mockMvc
        .perform(
            multipart("/api/users/me/avatar")
                .file(fichier)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.message").value("L'avatar ne doit pas dépasser 5 Mo"));
  }

  private String registerActivateAndLogin(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", email,
                            "password", "Password123!",
                            "firstName", "Alice",
                            "lastName", "Dupont"))))
        .andExpect(status().isCreated());

    User user = userRepository.findByEmail(email).orElseThrow();
    user.setActive(true);
    userRepository.save(user);

    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", "Password123!"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(body).get("accessToken").asText();
  }
}
