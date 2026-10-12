package com.ethan.familyadmin.transaction;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.transaction.dto.CreateTransactionRequest;
import com.ethan.familyadmin.transaction.dto.TransactionQuery;
import com.ethan.familyadmin.transaction.dto.TransactionResponse;
import com.ethan.familyadmin.transaction.dto.TransactionSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(transactionService.list(new TransactionQuery(month, type, category, page, pageSize)));
    }

    @GetMapping("/summary")
    public ApiResponse<TransactionSummaryResponse> summary(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(transactionService.summary(new TransactionQuery(month, type, category, 1, 100)));
    }

    @PostMapping
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.success(transactionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody CreateTransactionRequest request
    ) {
        return ApiResponse.success(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ApiResponse.success(null);
    }
}

