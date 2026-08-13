package fr.stockshop.stock_api.configuration;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProductPhotoWebConfig implements WebMvcConfigurer {

  @Value("${app.storage.product-photo-directory:uploads/products}")
  private String photosDirectory;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path directory = Path.of(photosDirectory).toAbsolutePath().normalize();
    String location = directory.toUri().toString();
    if (!location.endsWith("/")) {
      location = location + "/";
    }
    registry.addResourceHandler("/uploads/products/**").addResourceLocations(location);
  }
}
