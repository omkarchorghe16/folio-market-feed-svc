package com.stocknews.repository;

import com.stocknews.model.UpstoxStockQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UpstoxStockQuoteRepository extends JpaRepository<UpstoxStockQuote, Long> {
    List<UpstoxStockQuote> findAllByExchangeAndSymbolIn(String exchange, Collection<String> symbols);
}
