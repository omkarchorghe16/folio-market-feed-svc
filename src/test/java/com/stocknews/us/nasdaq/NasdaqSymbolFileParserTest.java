package com.stocknews.us.nasdaq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NasdaqSymbolFileParserTest {

    @Test
    void parsesNasdaqListedFileAndSkipsTestIssues() {
        List<NasdaqSymbol> symbols =
                NasdaqSymbolFileParser.parse(NasdaqSymbolFixtures.NASDAQ_LISTED, NasdaqSymbolFile.NASDAQ_LISTED);

        assertEquals(List.of(
                new NasdaqSymbol("AAPL", "Apple Inc. - Common Stock", NasdaqSymbolType.EQUITY, "NASDAQ"),
                new NasdaqSymbol("APLD", "Applied Digital Corporation - Common Stock", NasdaqSymbolType.EQUITY,
                        "NASDAQ"),
                new NasdaqSymbol("QQQ", "Invesco QQQ Trust, Series 1", NasdaqSymbolType.ETF, "NASDAQ")),
                symbols);
    }

    @Test
    void parsesOtherListedFileAndNamesTheExchange() {
        List<NasdaqSymbol> symbols =
                NasdaqSymbolFileParser.parse(NasdaqSymbolFixtures.OTHER_LISTED, NasdaqSymbolFile.OTHER_LISTED);

        assertEquals(List.of("BRK.B", "F", "SPY", "AMBA"), symbols.stream().map(NasdaqSymbol::symbol).toList());
        assertEquals(List.of("NYSE", "NYSE", "NYSE Arca", "NYSE American"),
                symbols.stream().map(NasdaqSymbol::exchange).toList());
        assertEquals(NasdaqSymbolType.ETF, symbols.get(2).type());
    }

    @Test
    void keepsUnknownExchangeCodeAsIs() {
        String file = """
                ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol
                NEWX|New Exchange Listing Inc. Common Stock|Q|NEWX|N|100|N|NEWX
                File Creation Time: 1120202621:31||||||
                """;

        assertEquals("Q", NasdaqSymbolFileParser.parse(file, NasdaqSymbolFile.OTHER_LISTED).get(0).exchange());
    }

    @Test
    void rejectsTruncatedEmptyOrUnrecognisedFiles() {
        String truncated = """
                Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares
                AAPL|Apple Inc. - Common Stock|Q|N|N|100|N|N
                """;

        assertThrows(IllegalArgumentException.class,
                () -> NasdaqSymbolFileParser.parse(truncated, NasdaqSymbolFile.NASDAQ_LISTED));
        assertThrows(IllegalArgumentException.class,
                () -> NasdaqSymbolFileParser.parse("  ", NasdaqSymbolFile.NASDAQ_LISTED));
        assertThrows(IllegalArgumentException.class,
                () -> NasdaqSymbolFileParser.parse("<html>Error</html>\nFile Creation Time: x",
                        NasdaqSymbolFile.OTHER_LISTED));
    }
}
