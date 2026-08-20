package fr.stockshop.stock_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StockApiApplicationTests {

  @Test
  void contextLoads(ApplicationContext applicationContext) {
    assertThat(applicationContext).isNotNull();
  }
}
