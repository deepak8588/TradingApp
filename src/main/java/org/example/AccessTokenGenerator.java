package org.example;

import org.example.Const.AppConstants;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.User;

import java.io.*;
import java.util.Properties;

public class AccessTokenGenerator {
    public static void main(String[] args) throws IOException {
        String fileName = AppConstants.TOKEN_FILENAME;
        Properties prop = new Properties();
        ClassLoader classLoader = AccessTokenGenerator.class.getClassLoader();
        InputStream is = classLoader.getResourceAsStream("KeysConfig.prop");
        prop.load(is);
        is.close();

        String requestToken = prop.getProperty("requestToken");
        String apiKey = prop.getProperty("apiKey");
        String apiSecret = prop.getProperty("apiSecret");
        KiteConnect kiteConnect = new KiteConnect(apiKey, true);
        String loginURL = kiteConnect.getLoginURL();

        User user;
        try {
            user = kiteConnect.generateSession(requestToken, apiSecret);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        prop.setProperty("accessToken", user.accessToken);
        OutputStream os = new FileOutputStream("resources/KeysConfig.prop");
        prop.store(os, null); // FileOutputStream
        os.close();
    }
}
