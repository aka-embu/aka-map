package example.com.map.demo;

import android.text.TextUtils;

import com.akamai.android.sdk.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import okhttp3.Authenticator;
import okhttp3.Connection;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;
import okhttp3.internal.http.HttpMethod;
import okhttp3.internal.http.StatusLine;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;

import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static okhttp3.internal.http.StatusLine.HTTP_PERM_REDIRECT;
import static okhttp3.internal.http.StatusLine.HTTP_TEMP_REDIRECT;

/**
 * Created by vaanand on 11/15/16.
 * Usage: See AkaOkHttpClientSample for GET/POST samples.
 */
public class AkaOkHttpInterceptor implements Interceptor {

    private static final int CHUNK_SIZE = 4096;
    private Authenticator mAuthenticator = null;
    public static boolean sFollowSslRedirects = false;

    public AkaOkHttpInterceptor(Authenticator authenticator) {
        mAuthenticator = authenticator;
    }

    public AkaOkHttpInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final Response userResponse = getResponse(chain.request());
        if (mAuthenticator != null && userResponse != null && userResponse.code() != HTTP_OK) {
            final Response authenticatedResponse = getAuthenticatedResponse(chain, userResponse);
            if (authenticatedResponse != null) {
                return authenticatedResponse;
            }
        }
        else if (userResponse != null && isRedirected(userResponse.code())) {
            final Response redirectedResponse = getRedirectedResponse(userResponse);
            if (redirectedResponse != null) {
                return redirectedResponse;
            }
        }
        return userResponse;
    }

    private Response getAuthenticatedResponse(Chain chain, Response response)  throws IOException {
        Response authenticatedResponse = null;
        final Connection connection = chain.connection();
        final Route route = connection != null
                ? connection.route()
                : null;
        final int responseCode = response.code();
        switch (responseCode) {
            case HTTP_PROXY_AUTH:
            case HTTP_UNAUTHORIZED:
                final Request newRequest = mAuthenticator.authenticate(route, response);
                if (newRequest != null) {
                    authenticatedResponse = getResponse(newRequest);
                }
                break;
            default:
                break;
        }
        return authenticatedResponse;
    }

    private Response getRedirectedResponse(Response response)  throws IOException {
        final int responseCode = response.code();
        final String method = response.request().method();

        switch (responseCode) {
            case HTTP_PERM_REDIRECT:
            case HTTP_TEMP_REDIRECT:
                // "If the 307 or 308 status code is received in response to a request other than GET
                // or HEAD, the user agent MUST NOT automatically redirect the request"
                if (!method.equals("GET") && !method.equals("HEAD")) {
                    return null;
                }
                // fall-through
            case HTTP_MULT_CHOICE:
            case HTTP_MOVED_PERM:
            case HTTP_MOVED_TEMP:
            case HTTP_SEE_OTHER:
                try {
                    // If the location is not present, return null.
                    String location = response.header("Location");
                    if (location == null) return null;
                    HttpUrl url = response.request().url().resolve(location);

                    // Don't follow redirects to unsupported protocols.
                    if (url == null) return null;

                    // If configured, don't follow redirects between SSL and non-SSL.
                    boolean sameScheme = url.scheme().equals(response.request().url().scheme());
                    if (!sameScheme && !sFollowSslRedirects) return null;

                    // Most redirects don't include a request body.
                    Request.Builder requestBuilder = response.request().newBuilder();
                    if (HttpMethod.permitsRequestBody(method)) {
                        //TODO:If you are getting error on HttpMethod.redirectsWithBody,
                        //change it to HttpMethod.INSTANCE.redirectsToGet
                        final boolean maintainBody = HttpMethod.redirectsWithBody(method);
                        //TODO:If you are getting error on HttpMethod.redirectsToGet,
                        //change it to HttpMethod.INSTANCE.redirectsToGet
                        if (HttpMethod.redirectsToGet(method)) {
                            requestBuilder.method("GET", null);
                        } else {
                            RequestBody requestBody = maintainBody ? response.request().body() : null;
                            requestBuilder.method(method, requestBody);
                        }
                        if (!maintainBody) {
                            requestBuilder.removeHeader("Transfer-Encoding");
                            requestBuilder.removeHeader("Content-Length");
                            requestBuilder.removeHeader("Content-Type");
                        }
                    }

                    // When redirecting across hosts, drop all authentication headers.
                    if (!sameConnection(response, url)) {
                        requestBuilder.removeHeader("Authorization");
                    }
                    return getResponse(requestBuilder.url(url).build());
                } catch (Exception ignored) {
                    Logger.dd("exception handling redirect");
                    return null;
                }
            default:
                return null;
        }
    }

    private Response getResponse(final Request request) throws IOException {
        HttpURLConnection connection = openConnection(request);
        prepareRequest(connection, request);
        return readResponse(connection, request);
    }

    protected HttpURLConnection openConnection(Request request) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(request.url().toString()).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        return connection;
    }

    void prepareRequest(HttpURLConnection connection, Request request) throws IOException {
        connection.setRequestMethod(request.method());
        connection.setDoInput(true);

        Headers requestHeaders = request.headers();
        for (String key : requestHeaders.names()) {
            connection.setRequestProperty(key, requestHeaders.get(key));
        }

        RequestBody body = request.body();
        if (body != null) {
            connection.setDoOutput(true);
            if( body.contentType() != null ) {
                connection.setRequestProperty("Content-Type", body.contentType().toString());
            }
            long length = body.contentLength();
            if (length != -1) {
                connection.setFixedLengthStreamingMode((int) length);
                connection.setRequestProperty("Content-Length", String.valueOf(length));
            } else {
                connection.setChunkedStreamingMode(CHUNK_SIZE);
            }
            Sink sink = Okio.sink(connection.getOutputStream());
            BufferedSink bufferedSink = Okio.buffer(sink);
            body.writeTo(bufferedSink);
            bufferedSink.flush();
        }
    }

    Response readResponse(HttpURLConnection connection, Request request) throws IOException {
        int status = connection.getResponseCode();
        if (status < 0) {
            status = 0;
        }
        String reason = connection.getResponseMessage();
        if (reason == null) reason = ""; // HttpURLConnection treats empty reason as null.

        Headers.Builder responseHeaderBuilder = new Headers.Builder();
        if (status > 0) {
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            if (headerFields != null && !headerFields.isEmpty()) {
                for (Map.Entry<String, List<String>> field : headerFields.entrySet()) {
                    String name = field.getKey();
                    if (!TextUtils.isEmpty(name)) {
                        for (String value : field.getValue()) {
                            if (!TextUtils.isEmpty(value)) {
                                responseHeaderBuilder.add(name, value);
                            }
                        }
                    }
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
        AkaResponseBody responseBody = new AkaResponseBody(mimeType, length, stream);
        Response response = new Response.Builder()
                .request(request)
                .headers(responseHeaderBuilder.build())
                .code(status)
                .message(reason)
                .protocol(Protocol.HTTP_1_1)
                .body(responseBody)
                .build();

        return response;
    }

    private static class AkaResponseBody extends ResponseBody {

        private final String mimeType;
        private final long length;
        private final InputStream stream;

        private AkaResponseBody(String mimeType, long length, InputStream stream) {
            this.mimeType = (mimeType != null) ? mimeType : "";
            this.length = length;
            this.stream = stream;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse(mimeType);
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public BufferedSource source() {
            if (stream == null) {
                return Okio.buffer(Okio.source(new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return -1;
                    }
                }));
            }
            return Okio.buffer(Okio.source(stream));
        }
    }

    private boolean sameConnection(Response response, HttpUrl followUp) {
        HttpUrl url = response.request().url();
        return url.host().equals(followUp.host())
                && url.port() == followUp.port()
                && url.scheme().equals(followUp.scheme());
    }

    private boolean isRedirected(int responseCode) {
        switch (responseCode) {
            case HTTP_PERM_REDIRECT:
            case HTTP_TEMP_REDIRECT:
            case HTTP_MULT_CHOICE:
            case HTTP_MOVED_PERM:
            case HTTP_MOVED_TEMP:
            case HTTP_SEE_OTHER:
                return true;
            default:
                return false;
        }
    }
}
