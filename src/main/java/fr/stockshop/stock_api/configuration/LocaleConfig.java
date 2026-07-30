package fr.stockshop.stock_api.configuration;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Internationalisation (i18n) : centralise la résolution des textes affichés à l'utilisateur
 * (messages de validation, d'erreur, emails) selon la langue de la requête ou celle enregistrée sur
 * le compte utilisateur (voir {@link fr.stockshop.stock_api.security.RequestLocaleFilter}).
 *
 * <p>Ajouter une nouvelle langue ne nécessite aucune modification de code : il suffit de créer un
 * fichier {@code messages_xx.properties} dans {@code src/main/resources/i18n/} et d'ajouter son
 * code à la propriété {@code app.i18n.supported-locales} (application.yml).
 */
@Configuration
public class LocaleConfig {

  @Value("#{'${app.i18n.supported-locales:fr,en}'.split(',')}")
  private List<String> supportedLocales;

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasenames("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    // Filet de sécurité : si une clé manque dans un bundle, on affiche le code plutôt que de
    // faire échouer la requête (NoSuchMessageException) à cause d'un simple oubli de traduction.
    source.setUseCodeAsDefaultMessage(true);
    return source;
  }

  /**
   * Fait résoudre les messages Bean Validation ({@code @NotBlank}, {@code @Size}...) via le {@link
   * MessageSource} ci-dessus, plutôt que le bundle JSR-380 par défaut.
   */
  @Bean
  public LocalValidatorFactoryBean validator(MessageSource messageSource) {
    LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
    factory.setValidationMessageSource(messageSource);
    return factory;
  }

  /**
   * Résolution de repli basée sur l'en-tête HTTP {@code Accept-Language}, utilisée tant qu'aucun
   * utilisateur authentifié n'est connu (inscription, connexion...).
   */
  @Bean
  public AcceptHeaderLocaleResolver localeResolver() {
    List<Locale> locales = supportedLocales.stream().map(Locale::forLanguageTag).toList();
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(locales.get(0));
    resolver.setSupportedLocales(locales);
    return resolver;
  }
}
