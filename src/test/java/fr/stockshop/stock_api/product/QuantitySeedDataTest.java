package fr.stockshop.stock_api.product;

import static org.assertj.core.api.Assertions.assertThat;

import fr.stockshop.stock_api.TestcontainersConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

/**
 * Vérifie les données de référence (types et unités de quantité) mises en place par la migration
 * Flyway au démarrage, utilisées pour valider et convertir les quantités de stock.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class QuantitySeedDataTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void quantityTypesShouldContainWeightLiquidAndUnit() {
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quantity_types", Integer.class);
    assertThat(count).isEqualTo(3);

    var codes =
        jdbcTemplate.queryForList("SELECT code FROM quantity_types ORDER BY code", String.class);
    assertThat(codes).containsExactly("liquid", "unit", "weight");
  }

  @Test
  void quantityUnitsShouldBeDistributedAcrossTypes() {
    Integer total =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quantity_units", Integer.class);
    assertThat(total).isEqualTo(12);

    assertThat(unitCountFor("weight")).isEqualTo(7);
    assertThat(unitCountFor("liquid")).isEqualTo(4);
    assertThat(unitCountFor("unit")).isEqualTo(1);
  }

  @Test
  void eachQuantityTypeShouldHaveExactlyOneBaseUnit() {
    var baseUnitCounts =
        jdbcTemplate.queryForList(
            """
            SELECT t.code AS type_code, COUNT(*) AS base_unit_count
            FROM quantity_units u
            JOIN quantity_types t ON t.id = u.quantity_type_id
            WHERE u.is_base_unit = TRUE
            GROUP BY t.code
            """);

    assertThat(baseUnitCounts).hasSize(3);
    baseUnitCounts.forEach(
        row -> assertThat(((Number) row.get("base_unit_count")).intValue()).isEqualTo(1));
  }

  @Test
  void conversionFactorsShouldMatchTheMetricReference() {
    assertThat(conversionFactorOf("weight", "kg")).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(conversionFactorOf("weight", "g")).isEqualByComparingTo(new BigDecimal("0.001"));
    assertThat(conversionFactorOf("weight", "mg")).isEqualByComparingTo(new BigDecimal("0.000001"));
    assertThat(conversionFactorOf("liquid", "L")).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(conversionFactorOf("liquid", "dL")).isEqualByComparingTo(new BigDecimal("0.1"));
    assertThat(conversionFactorOf("liquid", "cL")).isEqualByComparingTo(new BigDecimal("0.01"));
    assertThat(conversionFactorOf("liquid", "mL")).isEqualByComparingTo(new BigDecimal("0.001"));
    assertThat(conversionFactorOf("unit", "unit")).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void reapplyingTheSeedScriptShouldNotCreateDuplicates() throws IOException {
    String script =
        StreamUtils.copyToString(
            new ClassPathResource("db/migration/V2__seed_quantity_data.sql").getInputStream(),
            StandardCharsets.UTF_8);

    for (String statement : script.split(";")) {
      String sql = statement.strip();
      if (!sql.isEmpty()) {
        jdbcTemplate.execute(sql);
      }
    }

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quantity_types", Integer.class))
        .isEqualTo(3);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quantity_units", Integer.class))
        .isEqualTo(12);
  }

  private int unitCountFor(String typeCode) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM quantity_units u
        JOIN quantity_types t ON t.id = u.quantity_type_id
        WHERE t.code = ?
        """,
        Integer.class,
        typeCode);
  }

  private BigDecimal conversionFactorOf(String typeCode, String unitCode) {
    return jdbcTemplate.queryForObject(
        """
        SELECT u.conversion_factor FROM quantity_units u
        JOIN quantity_types t ON t.id = u.quantity_type_id
        WHERE t.code = ? AND u.code = ?
        """,
        BigDecimal.class,
        typeCode,
        unitCode);
  }
}
