package com.aubin.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {}
