package com.stocknews.india.nse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NseSymbolCsvParserTest {

    @Test
    void parsesEquityListWithPaddedHeadersAndQuotedNames() {
        List<NseSymbol> symbols = NseSymbolCsvParser.parse(NseSymbolFixtures.EQUITY_CSV, NseSymbolType.EQUITY);

        assertEquals(4, symbols.size());
        assertEquals(new NseSymbol("RELIANCE", "Reliance Industries Limited", NseSymbolType.EQUITY, "EQ", "INE002A01018"),
                symbols.get(0));
        assertEquals("Jio Financial Services Limited, Mumbai", symbols.get(3).name());
    }

    @Test
    void parsesEtfListUsingSecurityName() {
        List<NseSymbol> symbols = NseSymbolCsvParser.parse(NseSymbolFixtures.ETF_CSV, NseSymbolType.ETF);

        assertEquals(2, symbols.size());
        assertEquals("NIFTYBEES", symbols.get(0).symbol());
        assertEquals("Nippon India ETF Nifty 50 BeES", symbols.get(0).name());
        assertEquals("INF204KB14I2", symbols.get(0).isin());
        assertNull(symbols.get(0).series());
    }

    @Test
    void handlesByteOrderMarkAndEscapedQuotes() {
        String csv = "﻿SYMBOL,NAME OF COMPANY\nABC,\"The \"\"ABC\"\" Co\"\n";

        List<NseSymbol> symbols = NseSymbolCsvParser.parse(csv, NseSymbolType.EQUITY);

        assertEquals("The \"ABC\" Co", symbols.get(0).name());
    }

    @Test
    void rejectsEmptyFileHtmlPageAndHeaderOnlyFile() {
        assertThrows(IllegalArgumentException.class, () -> NseSymbolCsvParser.parse("  ", NseSymbolType.EQUITY));
        assertThrows(IllegalArgumentException.class,
                () -> NseSymbolCsvParser.parse("<html>Access Denied</html>", NseSymbolType.EQUITY));
        assertThrows(IllegalArgumentException.class,
                () -> NseSymbolCsvParser.parse("SYMBOL,NAME OF COMPANY\n", NseSymbolType.EQUITY));
    }
}
