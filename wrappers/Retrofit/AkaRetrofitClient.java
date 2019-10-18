package example.com.testapp;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

/**
 * Akamai Wrapper for Retrofit
 * Usage:
 *
 *
 String API_BASE_URL = "http://your.api-base.url";

 RestAdapter.Builder builder =
 new RestAdapter.Builder()
 .setEndpoint(API_BASE_URL)
 .setClient(new AkaRetrofitClient());

 RestAdapter adapter = builder.build();
 *
 */
public class AkaRetrofitClient implements Client {
    private static final int CHUNK_SIZE = 4096;

    @Override
    public Response execute(Request request) throws IOException {
        HttpURLConnection connection = openConnection(request);
        prepareRequest(connection, request);
        return readResponse(connection);
    }

    protected HttpURLConnection openConnection(Request request) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(request.getUrl()).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        return connection;
    }

    void prepareRequest(HttpURLConnection connection, Request request) throws IOException {
        connection.setRequestMethod(request.getMethod());
        connection.setDoInput(true);

        for (Header header : request.getHeaders()) {
            connection.setRequestProperty(header.getName(), header.getValue());
        }

        TypedOutput body = request.getBody();
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", body.mimeType());
            long length = body.length();
            if (length != -1) {
                connection.setFixedLengthStreamingMode((int) length);
                connection.setRequestProperty("Content-Length", String.valueOf(length));
            } else {
                connection.setChunkedStreamingMode(CHUNK_SIZE);
            }
            body.writeTo(connection.getOutputStream());
        }
    }

    Response readResponse(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        String reason = connection.getResponseMessage();
        if (status <= 0) {
            throw new IOException("Error connecting to the server");
        }
        if (reason == null) reason = ""; // HttpURLConnection treats empty reason as null.

        List<Header> headers = new ArrayList<Header>();
        Map<String, List<String>> urlConnectionHeaders = connection.getHeaderFields();
        if (urlConnectionHeaders != null && !urlConnectionHeaders.isEmpty()) {
            for (Map.Entry<String, List<String>> field : ((Map<String, List<String>>) urlConnectionHeaders).entrySet()) {
                String name = field.getKey();
                for (String value : field.getValue()) {
                    headers.add(new Header(name, value));
                }
            }
        }
        String mimeType = connection.getContentType();
        int length = connection.getContentLength();
        InputStream stream;
        if (status >= 400) {
            stream = connection.getErrorStream();
        } else {
            stream = connection.getInputStream();
        }
        TypedInput responseBody = new TypedInputStream(mimeType, length, stream);
        return new Response(connection.getURL().toString(), status, reason, headers, responseBody);
    }

    private static class TypedInputStream implements TypedInput {
        private final String mimeType;
        private final long length;
        private final InputStream stream;

        private TypedInputStream(String mimeType, long length, InputStream stream) {
            this.mimeType = mimeType;
            this.length = length;
            this.stream = stream;
        }

        @Override public String mimeType() {
            return mimeType;
        }

        @Override public long length() {
            return length;
        }

        @Override public InputStream in() throws IOException {
            return stream;
        }
    }
}
