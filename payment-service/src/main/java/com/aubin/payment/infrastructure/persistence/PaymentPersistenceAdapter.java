package com.aubin.payment.infrastructure.persistence;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.application.port.out.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return toDomain(jpaRepository.save(toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private PaymentJpaEntity toEntity(Payment p) {
        return new PaymentJpaEntity(p.id(), p.customerId(), p.amount(), p.currency(), p.status(), p.createdAt());
    }

    private Payment toDomain(PaymentJpaEntity e) {
        return new Payment(e.getId(), e.getCustomerId(), e.getAmount(), e.getCurrency(), e.getStatus(), e.getCreatedAt());
    }
}
