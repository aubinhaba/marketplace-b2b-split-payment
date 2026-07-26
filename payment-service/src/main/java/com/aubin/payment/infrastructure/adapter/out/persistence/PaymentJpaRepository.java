package com.aubin.payment.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Package-private so no bean can inject it directly and bypass the PaymentRepository port
interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {}
