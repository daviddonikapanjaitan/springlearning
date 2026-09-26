package com.course.springlearning.order.repository;

import com.course.springlearning.order.entity.Order;
import com.course.springlearning.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findAllByUser_Id(Long userId, Pageable pageable);

    List<Order> findAllByUser_IdAndOrderStatusOrderByIdAsc(Long userId, OrderStatus orderStatus);

    // Sum of total_price of one user's orders with the given status, 0 when there are none
    @Query(value = """
            SELECT CAST(COALESCE(SUM(total_price), 0) AS BIGINT)
            FROM orders
            WHERE users_id = :userId AND order_status = :orderStatus
            """, nativeQuery = true)
    long sumTotalPriceByUserIdAndOrderStatus(@Param("userId") Long userId, @Param("orderStatus") String orderStatus);

    // SELECT ... FOR UPDATE: the Kafka listener and the complete API cannot change the same order at once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Long id);
}
