package fr.stockshop.stock_api.configuration;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AvatarWebConfig implements WebMvcConfigurer {

  @Value("${app.storage.avatar-directory:uploads/avatars}")
  private String repertoireAvatars;

  @Override
  public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
    Path repertoire = Path.of(repertoireAvatars).toAbsolutePath().normalize();
    String location = repertoire.toUri().toString();
    if (!location.endsWith("/")) {
      location = location + "/";
    }
    registry.addResourceHandler("/uploads/avatars/**").addResourceLocations(location);
  }
}
