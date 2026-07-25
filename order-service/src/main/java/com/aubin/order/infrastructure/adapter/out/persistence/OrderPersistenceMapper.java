package com.aubin.order.infrastructure.adapter.out.persistence;

import com.aubin.commons.domain.Money;
import com.aubin.order.domain.model.Order;
import com.aubin.order.domain.model.OrderId;
import com.aubin.order.domain.model.OrderLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// default methods, not generated: Order has fluent accessors and no public constructor, and reconstitute is the only correct entry point
@Mapper(componentModel = "spring")
public interface OrderPersistenceMapper {

    @Mapping(target = "unitPriceAmount", source = "unitPrice.amount")
    @Mapping(target = "unitPriceCurrency", source = "unitPrice.currency")
    OrderLineEmbeddable toEmbeddable(OrderLine line);

    default OrderJpaEntity toJpaEntity(Order order) {
        if (order == null) {
            return null;
        }
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(UUID.fromString(order.id().value()));
        entity.setCustomerId(order.customerId());
        entity.setSellerId(order.sellerId());
        entity.setTotalAmount(order.total().amount());
        entity.setCurrency(order.total().currency());
        entity.setStatus(order.status());
        entity.setPlacedAt(order.placedAt());
        // Mutable list: Hibernate manages the @ElementCollection in place
        entity.setLines(order.lines().stream().map(this::toEmbeddable).collect(Collectors.toCollection(ArrayList::new)));
        return entity;
    }

    default Order toDomain(OrderJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<OrderLine> lines = entity.getLines().stream()
                .map(line -> new OrderLine(
                        line.getProductId(),
                        line.getProductName(),
                        line.getQuantity(),
                        Money.of(line.getUnitPriceAmount(), line.getUnitPriceCurrency())
                ))
                .toList();

        return Order.reconstitute(
                OrderId.from(entity.getId().toString()),
                entity.getCustomerId(),
                entity.getSellerId(),
                lines,
                Money.of(entity.getTotalAmount(), entity.getCurrency()),
                entity.getStatus(),
                entity.getPlacedAt()
        );
    }
}
