package fr.stockshop.stock_api.quantity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quantity_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuantityUnit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quantity_type_id", nullable = false)
  private QuantityType quantityType;

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 50)
  private String label;

  @Column(name = "conversion_factor", nullable = false, precision = 20, scale = 10)
  private BigDecimal conversionFactor;

  @Column(name = "is_base_unit", nullable = false)
  private boolean baseUnit;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
