package fr.stockshop.stock_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class StockApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(StockApiApplication.class, args);
  }
}
