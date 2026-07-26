package com.aubin.payment.infrastructure.adapter.out.persistence;

import com.aubin.payment.application.port.out.PaymentRepository;
import com.aubin.payment.domain.model.Payment;
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

    private PaymentJpaEntity toEntity(Payment payment) {
        return new PaymentJpaEntity(
                payment.id(), payment.orderId(), payment.sellerId(), payment.customerId(),
                payment.amount(), payment.currency(), payment.status(), payment.createdAt()
        );
    }

    private Payment toDomain(PaymentJpaEntity entity) {
        return new Payment(
                entity.getId(), entity.getOrderId(), entity.getSellerId(), entity.getCustomerId(),
                entity.getAmount(), entity.getCurrency(), entity.getStatus(), entity.getCreatedAt()
        );
    }
}
