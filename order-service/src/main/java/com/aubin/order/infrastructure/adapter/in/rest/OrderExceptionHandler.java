package com.aubin.order.infrastructure.adapter.in.rest;

import com.aubin.order.domain.exception.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Validation and malformed-JSON failures are left to Spring, which already emits ProblemDetail
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Order Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    // Covers both a malformed OrderId and a violated invariant from Order.place
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Request");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}
