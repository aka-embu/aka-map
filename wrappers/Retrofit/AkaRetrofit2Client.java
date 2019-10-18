package example.com.map.testapp;

import okhttp3.OkHttpClient;

/**
 * Akamai Wrapper for Retrofit 2
 * Usage:
 *
 *
 String API_BASE_URL = "http://www.akamai.com/";

 Retrofit retrofit = new Retrofit.Builder()
 .baseUrl(API_BASE_URL)
 .addConverterFactory(GsonConverterFactory.create())
 .client(AkaRetrofit2Client.getClient())
 .build();
 *
 */
public class AkaRetrofit2Client extends OkHttpClient {

    public static OkHttpClient getClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(new AkaOkHttpInterceptor())
                .build();
        return httpClient;
    }
}
