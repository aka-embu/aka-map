package example.com.map.testapp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * Created by vaanand on 11/15/16.
 * Usage:
 * Contains GET/POST samples using OkHttpClient. We make use of okhttp3.Interceptor interface implemented by AkaOkHttpInterceptor
 */
public class AkaOkHttpClientSample {

    public void get(String url) {
        try {

            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new AkaOkHttpInterceptor()).build();

            Request request = new Request.Builder()
                            .url(url)
                            .build();
            Response response = client.newCall(request).execute();
            int responseCode = response.code();
            // Download content if needed
            InputStream inputStream = new BufferedInputStream(response.body().byteStream());
            byte[] readBuffer = new byte[8192];
            int bytesRead;
            int total = 0;
            while ((bytesRead = inputStream.read(readBuffer)) != -1)
            {
                //Save readBuffer if needed!
                total += bytesRead;
            }
            // Close the stream once done with the download.
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void post(String url, String json) {
        try {

            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new AkaOkHttpInterceptor()).build();

            final byte []postData = json.getBytes();
            RequestBody body = new RequestBody() {

                @Override
                public long contentLength() throws IOException {
                    return postData.length;
                }

                @Override
                public MediaType contentType() {
                    return MediaType.parse("application/json; charset=utf-8");
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    sink.write(postData);
                    sink.flush();
                    sink.close();
                }
            };

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            Response response = client.newCall(request).execute();
            int responseCode = response.code();
            // Download content if needed
            InputStream inputStream = new BufferedInputStream(response.body().byteStream());
            byte[] readBuffer = new byte[8192];
            int bytesRead;
            int total = 0;
            while ((bytesRead = inputStream.read(readBuffer)) != -1)
            {
                //Save readBuffer if needed!
                total += bytesRead;
            }
            // Close the stream once done with the download.
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
