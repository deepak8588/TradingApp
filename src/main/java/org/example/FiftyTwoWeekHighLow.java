package org.example;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

public class FiftyTwoWeekHighLow {
    private static void updateFiftyTwoWeekHigh(Long instrumentToken, String tradingSymbol, KiteConnect kiteConnect, Map<String, Double> fiftyTwoWeekHighMap) {
        try {

            //todo: Check if this can be improved and remove all time high cases logic should actual breakout.
            DateFormat df = new SimpleDateFormat("MM-dd-yyyy");

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
