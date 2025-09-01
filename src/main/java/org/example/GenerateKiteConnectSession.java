package org.example;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Profile;
import com.zerodhatech.models.User;

import java.io.IOException;

public class GenerateKiteConnectSession {
    public static KiteConnect kiteSdk;
    public static String api_key;
    public static String req_token;
    public static String sec_key;
    public static void main(String[] args) {
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

}
