package com.example.jpa.repository;

import com.example.jpa.entity.Order;
import com.example.jpa.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByStatus(OrderStatus status);

    @Query("SELECT o FROM Order o JOIN FETCH o.member JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT o FROM Order o WHERE o.member.id = :memberId ORDER BY o.orderedAt DESC")
    List<Order> findByMemberId(@Param("memberId") Long memberId);
}
