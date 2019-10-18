package example.com.map.demo;

import android.content.Context;
import android.net.Uri;

import com.akamai.android.sdk.VocService;
import com.squareup.picasso.Downloader;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Available for Picasso below version 2.5
 *
 * Usage:
 Context ctx;
 Picasso.Builder picassoBuilder = new Picasso.Builder(ctx);
 picassoBuilder.downloader(new AkaPicassoDownloader(ctx));
 Picasso picasso = picassoBuilder.build();
 Picasso.setSingletonInstance(picasso);
 */
public class AkaPicassoDownloader implements Downloader {

    public AkaPicassoDownloader(Context context) {
        // initialize VocService
        VocService.createVocService(context);
    }

    @Override
    public Response load(Uri uri, int networkPolicy) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode <= 0 || responseCode >= 300) {
                throw new ResponseException(responseCode + " " + connection.getResponseMessage(),
                        networkPolicy, responseCode);
            }
            return new Response(new FetchedInputStream(connection.getInputStream()),
                    true, connection.getContentLength());
        } catch (Exception e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw e;
        }
    }

    @Override
    public void shutdown() {
    }

    private class FetchedInputStream extends FilterInputStream {

        public FetchedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }
}