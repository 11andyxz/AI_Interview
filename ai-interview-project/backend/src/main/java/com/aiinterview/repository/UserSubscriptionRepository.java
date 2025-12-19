package com.aiinterview.repository;

import com.aiinterview.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByUserIdAndStatus(Long userId, String status);
    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<UserSubscription> findByAlipaySubscriptionId(String alipaySubscriptionId);
}

