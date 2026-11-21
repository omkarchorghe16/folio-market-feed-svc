package com.stocknews.us.nasdaq;

/** Trimmed copies of Nasdaq Trader's nasdaqlisted.txt and otherlisted.txt layouts, including the footer. */
final class NasdaqSymbolFixtures {
    static final String NASDAQ_LISTED = """
            Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares
            AAPL|Apple Inc. - Common Stock|Q|N|N|100|N|N
            APLD|Applied Digital Corporation - Common Stock|G|N|N|100|N|N
            QQQ|Invesco QQQ Trust, Series 1|G|N|N|100|Y|N
            ZXZZT|NASDAQ TEST STOCK|G|Y|N|100|N|N
            File Creation Time: 1120202621:31|||||||
            """;

    static final String OTHER_LISTED = """
            ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol
            BRK.B|Berkshire Hathaway Inc. New Common Stock|N|BRK.B|N|100|N|BRK.B
            F|Ford Motor Company Common Stock|N|F|N|100|N|F
            SPY|State Street SPDR S&P 500 ETF Trust|P|SPY|Y|100|N|SPY
            AMBA|Example American Holdings Common Stock|A|AMBA|N|100|N|AMBA
            NTEST|NYSE Test Stock|N|NTEST|N|100|Y|NTEST
            File Creation Time: 1120202621:31||||||
            """;

    private NasdaqSymbolFixtures() {
    }
}
