package com.aubin.order.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Package-private on purpose: no other bean can inject it and bypass the OrderRepository port
interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {
}
