package com.stocknews.india.nse;

/** Trimmed copies of NSE's EQUITY_L.csv and eq_etfseclist.csv layouts (headers padded like NSE's). */
final class NseSymbolFixtures {
    static final String EQUITY_CSV = """
            SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE
            RELIANCE,Reliance Industries Limited,EQ,29-NOV-1995,10,1,INE002A01018,10
            TCS,Tata Consultancy Services Limited,EQ,25-AUG-2004,1,1,INE467B01029,1
            RELINFRA,Reliance Infrastructure Limited,EQ,08-JAN-1996,10,1,INE036A01016,10
            JIOFIN,"Jio Financial Services Limited, Mumbai",EQ,21-AUG-2023,10,1,INE758E01017,10
            """;

    static final String ETF_CSV = """
            Symbol,Underlying,SecurityName,DateofListing,MarketLot,ISINNumber,FaceValue
            NIFTYBEES,Nifty 50,Nippon India ETF Nifty 50 BeES,08-JAN-2002,1,INF204KB14I2,1
            GOLDBEES,Gold,Nippon India ETF Gold BeES,19-MAR-2007,1,INF204KB17I5,1
            """;

    private NseSymbolFixtures() {
    }
}
