package org.example;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

public class PlaceOrderExample {

    public static KiteConnect kiteSdk;
    public static void main(String[] args) {
        try {
            // Initialize KiteConnect
            Properties keysProp = new Properties();
            ClassLoader classLoader = OpenLowStrategy.class.getClassLoader();
            InputStream is2 = classLoader.getResourceAsStream("KeysConfig.prop");
            keysProp.load(is2);
            is2.close();

            String apiKey = keysProp.getProperty("apiKey");
            String accessToken = keysProp.getProperty("accessToken");

            kiteSdk = new KiteConnect(apiKey, true);
            kiteSdk.setAccessToken(accessToken);

            // Create order parameters
            OrderParams orderParams = new OrderParams();
            orderParams.exchange = "NSE";
            orderParams.tradingsymbol = "INFY";
            orderParams.transactionType = "BUY";
            orderParams.orderType = "MARKET";
            orderParams.quantity = 1;
            orderParams.product = "MIS";
            orderParams.validity = Constants.VALIDITY_DAY;

            // Place order
            //Get open orders
            List<Order> allOrders = kiteSdk.getOrders();

            if(noOpenOrder(allOrders)) {
                Order order = kiteSdk.placeOrder(orderParams, Constants.VARIETY_AMO);
                System.out.println("Order placed. Order ID: " + order.orderId);
            }

            orderParams = new OrderParams();
            orderParams.exchange = "NSE"; // NSE or BSE
            orderParams.tradingsymbol = "INFY";
            orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL; // or BUY
            orderParams.orderType = Constants.ORDER_TYPE_SL; // Use SL-M for stop loss market
            orderParams.product = Constants.PRODUCT_CNC; // CNC, MIS, or NRML
            orderParams.validity = Constants.VALIDITY_DAY;

            orderParams.quantity = 1;
            orderParams.price = 1400.00;        // Limit price (ignored in SL-M)
            orderParams.triggerPrice = 1405.00; // Trigger price

            // Place the stop loss order
            Order order = kiteSdk.placeOrder(orderParams, Constants.VARIETY_AMO);
            System.out.println("Stop Loss Order placed. Order ID: " + order.orderId);

        } catch (KiteException | IOException e) {
            System.err.println("Failed to place order: " + e.getMessage());
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
}