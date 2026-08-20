package fr.stockshop.stock_api.shoppinglist.entity;

import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.quantity.entity.QuantityUnit;
import fr.stockshop.stock_api.user.entity.User;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "shopping_list_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppingListItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "is_checked", nullable = false)
  @Builder.Default
  private boolean checked = false;

  @Column(name = "checked_quantity", precision = 10, scale = 3)
  private BigDecimal checkedQuantity;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "checked_unit_id")
  private QuantityUnit checkedUnit;

  @Column(name = "added_automatically", nullable = false)
  @Builder.Default
  private boolean addedAutomatically = false;

  @CreationTimestamp
  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt;

  @Column(name = "checked_at")
  private Instant checkedAt;
}
