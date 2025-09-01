package org.example;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.*;
import com.zerodhatech.ticker.KiteTicker;
import org.example.Const.AppConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class OpenHighStrategy {

    public static KiteConnect kiteSdk;

    public static void main(String[] args) throws IOException {
        // Replace with your API key and access token
        String keysFileName = AppConstants.TOKEN_FILENAME;
        Properties keysProp = new Properties();
        ClassLoader classLoader = OpenHighStrategy.class.getClassLoader();
        InputStream is2 = classLoader.getResourceAsStream("KeysConfig.prop");
        keysProp.load(is2);
        is2.close();

        String apiKey = keysProp.getProperty("apiKey");
        String accessToken = keysProp.getProperty("accessToken");

        kiteSdk = new KiteConnect(apiKey, true);
        kiteSdk.setAccessToken(accessToken);

        ArrayList<Long> tokens = new ArrayList<>();
        ArrayList<String> niftyFiveHundred;
        ArrayList<Instrument> stockToMonitor = new ArrayList<>();

        try {
            // Get the list of all NSE stocks
            List<Instrument> nifty500Stocks = kiteSdk.getInstruments("NSE");

            //Get the list of Nifty 500 stocks.
            String excelFilePath = "src/main/resources/NiftyStocks.xlsx";
            niftyFiveHundred = ExcelColumnReader.readExcelColumn(excelFilePath, 2);

            for(Instrument instrument: nifty500Stocks){
                if(niftyFiveHundred.contains(instrument.tradingsymbol)){
                    stockToMonitor.add(instrument);
                    tokens.add(instrument.getInstrument_token());
                }
            }

            // Initialize KiteTicker
            KiteTicker tickerProvider = new KiteTicker(kiteSdk.getAccessToken(), apiKey);

            // Event handler for tick updates
            tickerProvider.setOnTickerArrivalListener(ticks -> {
                for (Tick tick : ticks) {
                    String tradingSymbol = getTradingSymbol(stockToMonitor, tick.getInstrumentToken());
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
        if ((tick.getOpenPrice() == tick.getHighPrice())
                && (tick.getLastTradedPrice() < tick.getOpenPrice())
                && (tick.getLastTradedPrice() < 1000d)
                && (tick.getLastTradedPrice() > 100d)
                && (tick.getVolumeTradedToday() > 100000000L)
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

        //Logic to get quantity.
        int quantity = getQuantity(symbol);

        //Calculate stop loss.
        double stopLoss = openPrice;
        //Calculate profit target.
        double profitTarget =  lastTradedPrice - ((openPrice-lastTradedPrice) * 2);

        //Set order params
        OrderParams orderParams = new OrderParams();
        orderParams.quantity = quantity;
        orderParams.orderType = Constants.ORDER_TYPE_LIMIT;
        orderParams.tradingsymbol = symbol;
        orderParams.product = Constants.PRODUCT_MIS;
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.price = lastTradedPrice;
        orderParams.tag = "DeepakAlgoOrder";

        //Place order
        Order order = kiteSdk.placeOrder(orderParams, Constants.VARIETY_REGULAR);
        System.out.println("Order successfully placed with Order id:" + order.orderId);

        //Place SL order
        orderParams.transactionType = Constants.TRANSACTION_TYPE_BUY;
        orderParams.price = lastTradedPrice+2L;
        orderParams.tag = "DeepakAlgoOrder1";
        Order buyOrder = kiteSdk.placeOrder(orderParams, Constants.VARIETY_REGULAR);
        System.out.println("Stoploss order successfully placed with Order id:" + buyOrder.orderId);
    }

    private static int getQuantity(String symbol) throws IOException, KiteException {
        MarginCalculationParams marginParams = new MarginCalculationParams();
        marginParams.exchange = "NSE";
        marginParams.tradingSymbol = symbol;
        marginParams.orderType = "MARKET";
        marginParams.quantity = 1;
        marginParams.product = "MIS";
        marginParams.variety = "CO";
        marginParams.transactionType = "BUY";
        List<MarginCalculationParams> params = new ArrayList<>();
        params.add(marginParams);
        List<MarginCalculationData> data = null;

        data = kiteSdk.getMarginCalculation(params);

        double margin = data.get(0).total;

        return (int) (5000d / margin);
    }
}
