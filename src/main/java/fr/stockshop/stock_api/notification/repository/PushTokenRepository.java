package fr.stockshop.stock_api.notification.repository;

import fr.stockshop.stock_api.notification.entity.PushToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

  Optional<PushToken> findByToken(String token);

  @Query("SELECT pt FROM PushToken pt JOIN FETCH pt.user WHERE pt.user.id IN :userIds")
  List<PushToken> findByUserIdIn(@Param("userIds") Collection<UUID> userIds);

  void deleteByTokenIn(Collection<String> tokens);
}
