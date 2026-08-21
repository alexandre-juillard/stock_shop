package fr.stockshop.stock_api.stock.repository;

import fr.stockshop.stock_api.product.entity.Product;
import fr.stockshop.stock_api.stock.entity.StockItem;
import fr.stockshop.stock_api.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

  boolean existsByUserAndProduct(User user, Product product);

  @Query(
      "SELECT s FROM StockItem s "
          + "WHERE s.user = :user AND s.product.visible = true "
          + "ORDER BY s.product.category.name ASC, s.product.name ASC")
  List<StockItem> findVisibleByUserOrderByCategoryAndProductName(@Param("user") User user);

  /**
   * Candidats potentiels au job de notification d'expiration : le filtre définitif sur le délai
   * d'alerte propre à chaque utilisateur est appliqué en mémoire (non portable en JPQL), cette
   * requête ne fait que pré-filtrer en une seule fois.
   */
  @Query(
      "SELECT s FROM StockItem s JOIN FETCH s.user JOIN FETCH s.product "
          + "WHERE s.expirationDate IS NOT NULL "
          + "AND (s.lastExpiryNotifiedAt IS NULL OR s.lastExpiryNotifiedAt <> CURRENT_DATE)")
  List<StockItem> findExpirationNotificationCandidates();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE StockItem s SET s.lastExpiryNotifiedAt = :today WHERE s.id IN :ids")
  int markExpiryNotified(@Param("ids") List<UUID> ids, @Param("today") LocalDate today);
}
