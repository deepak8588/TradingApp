package org.example;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.*;
import com.zerodhatech.ticker.KiteTicker;
import org.example.Const.AppConstants;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class OptionOpenLowStrategy {

    public static KiteConnect kiteSdk;

    public static void main(String[] args) throws IOException {
        String keysFileName = AppConstants.TOKEN_FILENAME;
        Properties keysProp = new Properties();
        ClassLoader classLoader = OpenLowStrategy.class.getClassLoader();
        InputStream is2 = classLoader.getResourceAsStream("KeysConfig.prop");
        keysProp.load(is2);
        is2.close();

        String apiKey = keysProp.getProperty("apiKey");
        String accessToken = keysProp.getProperty("accessToken");

        kiteSdk = new KiteConnect(apiKey, true);
        kiteSdk.setAccessToken(accessToken);

        // Specify the exchange code (e.g., NSE) and segment (e.g., "OPT" for options)
        String exchange = "NSE";
        String segment = "OPT";
        ArrayList<Long> tokens = new ArrayList<>();
        ArrayList<String> niftyFiveHundred;
        ArrayList<Instrument> stockToMonitor = new ArrayList<>();
        String[] instrumentTokens= new String[]{"256265"};
        try {
            // Get the list of all NSE stocks
            List<Instrument> niftyOptions = kiteSdk.getInstruments("NFO");
            final Map<String, Quote> quote = kiteSdk.getQuote(instrumentTokens);
            Quote nifty50 = quote.get("256265");
            // Filter instruments to get options data
            List<Instrument> inTheMoneyCallAndPut = filterInTheMoneyCallAndPut(niftyOptions, nifty50);
            for(Instrument instrument: inTheMoneyCallAndPut){
                tokens.add(instrument.getInstrument_token());
            }

            // Initialize KiteTicker
            KiteTicker tickerProvider = new KiteTicker(kiteSdk.getAccessToken(), apiKey);

            // Event handler for tick updates
            tickerProvider.setOnTickerArrivalListener(ticks -> {
                for (Tick tick : ticks) {
                    String tradingSymbol = getTradingSymbol(inTheMoneyCallAndPut, tick.getInstrumentToken());
                    if (tradingSymbol != null) {
                        // Check open equal low logic.
                        try {
                            handleTickData(tradingSymbol, tick);
                        } catch (IOException | KiteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            // Make sure this is called before calling connect.
            tickerProvider.setTryReconnection(true);
            //maximum retries and should be greater than 0
            tickerProvider.setMaximumRetries(10);
            //set maximum retry interval in seconds
            tickerProvider.setMaximumRetryInterval(30);

            tickerProvider.setMode(tokens, KiteTicker.modeQuote);
            // Connect to the ticker
            tickerProvider.connect();

            // Subscribe to ticks for all NIFTY 500 stocks
            tickerProvider.subscribe(tokens);

            boolean isConnected = tickerProvider.isConnectionOpen();
            System.out.println("tickerProvider connected - " + isConnected);

        } catch (IOException | KiteException e) {
            e.printStackTrace();
        }
    }

    private static List<Instrument> filterInTheMoneyCallAndPut(List<Instrument> allInstruments, Quote nifty50) {
        //todo: filter the current expiry options data.
        final double lastPrice = nifty50.lastPrice;
        String targetExpiryDate = "2023-12-21";
        // Filter instruments based on the exchange and segment (e.g., "OPTSTK" for stock options)
        return allInstruments.stream()
                .filter(instrument -> instrument.name.equals("NIFTY")
                && ((instrument.instrument_type.equals("CE") && Double.parseDouble(instrument.getStrike()) < lastPrice)
                || (instrument.instrument_type.equals("PE") && Double.parseDouble(instrument.getStrike()) > lastPrice)))
                .collect(Collectors.toList());
    }

    private static String getTradingSymbol(List<Instrument> instruments, long instrumentToken) {
        for (Instrument instrument : instruments) {
            if (instrument.instrument_token == instrumentToken) {
                return instrument.tradingsymbol;
            }
        }
        return null;
    }

    private static void handleTickData(String tradingSymbol,
                                       Tick tick) throws IOException, KiteException {
        //Get open orders
        List<Order> allOrders = kiteSdk.getOrders();

        // Check if the current price is above the breakout threshold
        if ((tick.getOpenPrice() == tick.getLowPrice())
                && (tick.getLastTradedPrice() > tick.getOpenPrice())
                && (tick.getVolumeTradedToday() > 1000000L)
                && noOpenOrder(allOrders)) {
            // Place a market order on the breakout
            System.out.println("Placing order for: " + tradingSymbol +" at last traded price");
            placeMarketOrder(tradingSymbol, tick.getLastTradedPrice(), tick.getOpenPrice());
        }
    }

    private static boolean noOpenOrder(List<Order> allOrders) {
        boolean placeOrder = true;
        for (Order order : allOrders) {
            if ("OPEN".equals(order.status)) {
                placeOrder = false;
                break;
            }
        }
        return placeOrder;
    }

    private static void placeMarketOrder(String symbol, double lastTradedPrice, double openPrice) throws IOException, KiteException {
        // Place a market order for the specified symbol and quantity
        // Implement placeOrder logic using Kite Connect API


        //Calculate stop loss.
        double stopLoss = openPrice;
        //Calculate profit target.
        double profitTarget =  lastTradedPrice + ((lastTradedPrice-openPrice) * 2);

        //Set order params
        OrderParams orderParams = new OrderParams();
        orderParams.quantity = 50;
        orderParams.orderType = Constants.ORDER_TYPE_LIMIT;
        orderParams.tradingsymbol = symbol;
        orderParams.product = Constants.PRODUCT_MIS;
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.transactionType = Constants.TRANSACTION_TYPE_BUY;
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.stoploss = stopLoss;
        orderParams.squareoff = profitTarget;
        orderParams.price = lastTradedPrice;
        orderParams.tag = "DeepakAlgoOrder";

        //Place order
        Order order = kiteSdk.placeOrder(orderParams, Constants.VARIETY_REGULAR);
        System.out.println("Order successfully placed with Order id:" + order.orderId);
    }
}
