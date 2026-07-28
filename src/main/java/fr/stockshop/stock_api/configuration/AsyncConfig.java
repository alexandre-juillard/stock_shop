package fr.stockshop.stock_api.configuration;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Active l'exécution asynchrone des méthodes {@code @Async} (notamment {@link
 * fr.stockshop.stock_api.mail.EmailService}), afin que l'envoi d'un email ne bloque jamais la
 * réponse HTTP de l'utilisateur.
 *
 * <p>{@link #getAsyncUncaughtExceptionHandler()} est un filet de sécurité complémentaire à la
 * gestion d'erreur déjà présente dans {@code EmailService} : toute exception qui échapperait malgré
 * tout à une méthode asynchrone est journalisée via SLF4J plutôt que silencieusement affichée sur
 * la sortie standard (comportement par défaut de Spring).
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

  @Override
  @Bean(name = "taskExecutor")
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("async-task-");
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (Throwable ex, Method method, Object... params) ->
        log.error("Erreur non interceptée dans la méthode asynchrone {}", method.getName(), ex);
  }
}
