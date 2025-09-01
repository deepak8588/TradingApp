package org.example;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.*;
import com.zerodhatech.ticker.KiteTicker;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlaceOrderOnBreakoutForNifty500RealTime {

    //todo: Enhance this API for sell orders as well.
    public static KiteConnect kiteSdk;

    public static void main(String[] args) {
        // Replace with your API key and access token
        String req_token = "O3ia6Cs8o8CIyQlNGIGy5aGO5elUTJrH";
        String api_key = "ozr93gp1ynzy4b0u";
        String sec_key = "bp71ckhe3xdrbcl9rpxymunn7200jmj8";
        generateSession(req_token, api_key, sec_key);

        // Map to store 52-week high for each stock
        Map<String, Double> fiftyTwoWeekHighMap = new HashMap<>();
        ArrayList<Long> tokens = new ArrayList<>();

        try {
            // Get the list of NIFTY 500 stocks
            List<Instrument> nifty500Stocks = kiteSdk.getInstruments("NSE");

            List<Instrument> niftyFifty = nifty500Stocks.stream().filter(instrument -> instrument.tradingsymbol.equals("ACC")).collect(Collectors.toList());
            // Fetch 52-week high from historical data and update the map
            for(Instrument stock: niftyFifty) {
                updateFiftyTwoWeekHigh(stock.getInstrument_token(), stock.getTradingsymbol(), kiteSdk, fiftyTwoWeekHighMap);
                tokens.add(stock.getInstrument_token());
            }

            // Initialize KiteTicker
            KiteTicker tickerProvider = new KiteTicker(kiteSdk.getAccessToken(), api_key);

            // Event handler for tick updates
            tickerProvider.setOnTickerArrivalListener(ticks -> {
                for (Tick tick : ticks) {
                    String tradingSymbol = getTradingSymbol(nifty500Stocks, tick.getInstrumentToken());
                    if (tradingSymbol != null) {
                        // Check for breakout using tick data
                        try {
                            handleTickData(tradingSymbol, fiftyTwoWeekHighMap, tick);
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

    private static void generateSession(String req_token, String api_key, String sec_key) {
        kiteSdk = new KiteConnect(api_key, true);
        kiteSdk.setUserId("YO4077");
        User users = null;
        try {
            users = kiteSdk.generateSession(req_token, sec_key);
        } catch (KiteException | IOException ex) {
            throw new RuntimeException(ex);
        }
        kiteSdk.setAccessToken(users.accessToken);
        kiteSdk.setPublicToken(users.publicToken);
        try {
            Profile profile = kiteSdk.getProfile();
            System.out.println(profile.userName);
        } catch (IOException | KiteException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String getTradingSymbol(List<Instrument> instruments, long instrumentToken) {
        for (Instrument instrument : instruments) {
            if (instrument.instrument_token == instrumentToken) {
                return instrument.tradingsymbol;
            }
        }
        return null;
    }

    private static void handleTickData(String tradingSymbol, Map<String, Double> fiftyTwoWeekHighMap,
                                       Tick tick) throws IOException, KiteException {
        // Fetch or calculate 52-week high
        double fiftyTwoWeekHigh = fiftyTwoWeekHighMap.getOrDefault(tradingSymbol, 0.0);

        // Replace this threshold with the actual condition for a breakout
        double breakoutThreshold = fiftyTwoWeekHigh * 1.02; // 2% above 52-week high

        //Get open orders

        List<Order> openOrders = kiteSdk.getOrders();

        //todo: Add a logic for gap up.

        // Check if the current price is above the breakout threshold
        if (tick.getLastTradedPrice() > fiftyTwoWeekHigh
                && tick.getLastTradedPrice() < breakoutThreshold
                && tick.getOpenPrice() < fiftyTwoWeekHigh
                && tick.getVolumeTradedToday() > 1000000L
                && noOpenOrder(openOrders)) {
            // Place a market order on the breakout
            placeMarketOrder(tradingSymbol, tick.getLastTradedPrice());
        }
    }

    private static boolean noOpenOrder(List<Order> openOrders) {
        boolean isNoOpenOrder = false;
        if (openOrders == null || openOrders.size() == 0) {
            isNoOpenOrder = true;
        }
        return isNoOpenOrder;
    }

    private static void placeMarketOrder(String symbol, double lastTradedPrice) throws IOException, KiteException {
        // Place a market order for the specified symbol and quantity
        // Implement placeOrder logic using Kite Connect API


        //todo: 1. For SL and TP round off number to 0 decimal places as we dealing with stocks only.
        //todo: 2. Use margin api to get the correct quantity.

        //Calculate stop loss.
        double stopLoss = lastTradedPrice * 1.01;
        //Calculate profit target.
        double profitTarget = lastTradedPrice * 1.02;

        //Set order params
        OrderParams orderParams = new OrderParams();
        orderParams.quantity = 1;
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

    private static void updateFiftyTwoWeekHigh(Long instrumentToken, String tradingSymbol, KiteConnect kiteConnect, Map<String, Double> fiftyTwoWeekHighMap) {
        try {
            DateFormat df = new SimpleDateFormat("MM-dd-yyyy");

            //todo: Check if this can be improved and remove all time high cases logic should actual breakout.

            // Fetch historical data for the specified stock
            HistoricalData historicalData =  kiteConnect.getHistoricalData(
                    df.parse("11-01-2022"), // Start date
                    df.parse("24-11-2023"), // End date
                    String.valueOf(instrumentToken),
                    "day",         // Interval (day, minute, etc.)
                    false,           // Continuous data (false for historical data)
                    false
            );

            List<HistoricalData> historicalDataList = historicalData.dataArrayList;
            // Find the maximum closing price in the historical data
            double fiftyTwoWeekHigh = 0.0;
            for (HistoricalData hisData : historicalDataList) {
                if (hisData.high > fiftyTwoWeekHigh) {
                    fiftyTwoWeekHigh = hisData.high;
                }
            }

            // Update the map with the 52-week high value.
            fiftyTwoWeekHighMap.put(tradingSymbol, fiftyTwoWeekHigh);

        } catch (IOException | KiteException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
