package com.aubin.payment.infrastructure.adapter.in.rest.dto;

import com.aubin.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de sortie renvoyé par l'API REST après une opération sur un paiement.
 *
 * <p><b>Position en hexagonal</b> : {@code infrastructure.adapter.in.rest.dto}.
 * Ce record représente la représentation JSON du paiement — il ne contient
 * que ce que le client API a besoin de voir. Le domain model {@code Payment}
 * peut évoluer sans forcément changer ce DTO.
 *
 * <p><b>Séparer DTO et domain model</b> : un jour on pourra vouloir ne pas
 * exposer certains champs internes (ex: un ID interne de transaction PSP)
 * tout en les gardant dans le domain. La séparation permet ça.
 */
public record PaymentResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt
) {}
