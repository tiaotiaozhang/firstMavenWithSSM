package com.zcb.firstMvn.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * Useful utilities for working with UrlConnections and IO.
 *
 *  Anyone using the deprecated api calls for proxying to urls should update to use the new suggested calls.
 *  To let the jvm proxy for you automatically, use the following -D parameters:
 *
 *  http.proxyHost
 *  http.proxyPort (default: 80)
 *  http.nonProxyHosts (should always include localhost)
 *  https.proxyHost
 *  https.proxyPort
 *
 *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
 *
 * @author Ken Partlow
 * @author John DeRegnaucourt (john@cedarsoftware.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public final class UrlUtilities
{
    public static String globalUserAgent = null;
    public static String globalReferrer = null;
    public static final ThreadLocal<String> userAgent = new ThreadLocal<>();
    public static final ThreadLocal<String> referrer = new ThreadLocal<>();
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String SET_COOKIE_SEPARATOR = "; ";
    public static final String COOKIE = "Cookie";
    public static final String COOKIE_VALUE_DELIMITER = ";";
    public static final String PATH = "path";
    public static final String EXPIRES = "expires";
    public static final SafeSimpleDateFormat DATE_FORMAT = new SafeSimpleDateFormat("EEE, dd-MMM-yyyy hh:mm:ss z");
    public static final char NAME_VALUE_SEPARATOR = '=';
    public static final char DOT = '.';

    private static final Pattern resPattern = Pattern.compile("^res\\:\\/\\/", Pattern.CASE_INSENSITIVE);
    private static final Logger LOG = LogManager.getLogger(UrlUtilities.class);

    public static final TrustManager[] NAIVE_TRUST_MANAGER = new TrustManager[]
    {
            new X509TrustManager()
            {
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException
                {
                }
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException
                {
                }
                public X509Certificate[] getAcceptedIssuers()
                {
                    return null;
                }
            }
    };

    public static final HostnameVerifier NAIVE_VERIFIER = new HostnameVerifier()
    {
        public boolean verify(String s, SSLSession sslSession)
        {
            return true;
        }
    };

    public static SSLSocketFactory naiveSSLSocketFactory;

    static
    {
        try
        {
            // Default new HTTP connections to follow redirects
            HttpURLConnection.setFollowRedirects(true);
        }
        catch (Exception ignored) {}

        try
        {
            //  could be other algorithms (prob need to calculate this another way.
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, NAIVE_TRUST_MANAGER, new SecureRandom());
            naiveSSLSocketFactory = sslContext.getSocketFactory();
        }
        catch (Exception e)
        {
            LOG.warn("Failed to build Naive SSLSocketFactory", e);
        }
    }

    private UrlUtilities()
    {
        super();
    }

    public static void clearGlobalUserAgent()
    {
        globalUserAgent = null;
    }

    public static void clearGlobalReferrer()
    {
        globalReferrer = null;
    }

    public static void setReferrer(String referer)
    {
        if (StringUtilities.isEmpty(globalReferrer))
        {
            globalReferrer = referer;
        }
        referrer.set(referer);
    }

    public static String getReferrer()
    {
        String localReferrer = referrer.get();
        if (StringUtilities.hasContent(localReferrer))
        {
            return localReferrer;
        }
        return globalReferrer;
    }

    public static void setUserAgent(String agent)
    {
        if (StringUtilities.isEmpty(globalUserAgent))
        {
            globalUserAgent = agent;
        }
        userAgent.set(agent);
    }

    public static String getUserAgent()
    {
        String localAgent = userAgent.get();
        if (StringUtilities.hasContent(localAgent))
        {
            return localAgent;
        }
        return globalUserAgent;
    }

    public static void readErrorResponse(URLConnection c)
    {
        if (c == null)
        {
            return;
        }
        InputStream in = null;
        try
        {
            int error = ((HttpURLConnection) c).getResponseCode();
            in = ((HttpURLConnection) c).getErrorStream();
            if (in == null)
            {
                return;
            }
            LOG.warn("HTTP error response: " + ((HttpURLConnection) c).getResponseMessage());
            // read the response body
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            int count;
            byte[] bytes = new byte[8192];
            while ((count = in.read(bytes)) != -1)
            {
                out.write(bytes, 0, count);
            }
            LOG.warn("HTTP error Code:  " + error);
        }
        catch (ConnectException e)
        {
            LOG.error("Connection exception trying to read HTTP error response", e);
        }
        catch (IOException e)
        {
            LOG.error("IO Exception trying to read HTTP error response", e);
        }
        catch (Exception e)
        {
            LOG.error("Exception trying to read HTTP error response", e);
        }
        finally
        {
            IOUtilities.close(in);
        }
    }

    public static void disconnect(HttpURLConnection c)
    {
        if (c != null)
        {
            try
            {
                c.disconnect();
            }
            catch (Exception ignored) {}
        }
    }

    /**
     * Retrieves and stores cookies returned by the host on the other side of
     * the open java.net.URLConnection.
     * <p>
     * The connection MUST have been opened using the connect() method or a
     * IOException will be thrown.
     *
     * @param conn a java.net.URLConnection - must be open, or IOException will
     *             be thrown
     */
    public static void getCookies(URLConnection conn, Map store)
    {
        // let's determine the domain from where these cookies are being sent
        String domain = getCookieDomainFromHost(conn.getURL().getHost());
        Map domainStore; // this is where we will store cookies for this domain

        // now let's check the store to see if we have an entry for this domain
        if (store.containsKey(domain))
        {
            // we do, so lets retrieve it from the store
            domainStore = (Map) store.get(domain);
        }
        else
        {
            // we don't, so let's create it and put it in the store
            domainStore = new ConcurrentHashMap();
            store.put(domain, domainStore);
        }

        if (domainStore.containsKey("JSESSIONID"))
        {
            // No need to continually get the JSESSIONID (and set-cookies header) as this does not change throughout the session.
            return;
        }

        // OK, now we are ready to get the cookies out of the URLConnection
        String headerName;
        for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++)
        {
            if (headerName.equalsIgnoreCase(SET_COOKIE))
            {
                Map cookie = new ConcurrentHashMap();
                StringTokenizer st = new StringTokenizer(conn.getHeaderField(i), COOKIE_VALUE_DELIMITER);

                // the specification dictates that the first name/value pair
                // in the string is the cookie name and value, so let's handle
                // them as a special case:

                if (st.hasMoreTokens())
                {
                    String token = st.nextToken();
                    String key = token.substring(0, token.indexOf(NAME_VALUE_SEPARATOR)).trim();
                    String value = token.substring(token.indexOf(NAME_VALUE_SEPARATOR) + 1);
                    domainStore.put(key, cookie);
                    cookie.put(key, value);
                }

                while (st.hasMoreTokens())
                {
                    String token = st.nextToken();
                    int pos = token.indexOf(NAME_VALUE_SEPARATOR);
                    if (pos != -1)
                    {
                        String key = token.substring(0, pos).toLowerCase().trim();
                        String value = token.substring(token.indexOf(NAME_VALUE_SEPARATOR) + 1);
                        cookie.put(key, value);
                    }
                }
            }
        }
    }

    /**
     * Prior to opening a URLConnection, calling this method will set all
     * unexpired cookies that match the path or subpaths for thi underlying URL
     * <p>
     * The connection MUST NOT have been opened
     * method or an IOException will be thrown.
     *
     * @param conn a java.net.URLConnection - must NOT be open, or IOException will be thrown
     * @throws IOException Thrown if conn has already been opened.
     */
    public static void setCookies(URLConnection conn, Map store) throws IOException
    {
        // let's determine the domain and path to retrieve the appropriate cookies
        URL url = conn.getURL();
        String domain = getCookieDomainFromHost(url.getHost());
        String path = url.getPath();

        Map domainStore = (Map) store.get(domain);
        if (domainStore == null)
        {
            return;
        }
        StringBuilder cookieStringBuffer = new StringBuilder();
        Iterator cookieNames = domainStore.keySet().iterator();

        while (cookieNames.hasNext())
        {
            String cookieName = (String) cookieNames.next();
            Map cookie = (Map) domainStore.get(cookieName);
            // check cookie to ensure path matches and cookie is not expired
            // if all is cool, add cookie to header string
            if (comparePaths((String) cookie.get(PATH), path) && isNotExpired((String) cookie.get(EXPIRES)))
            {
                cookieStringBuffer.append(cookieName);
                cookieStringBuffer.append('=');
                cookieStringBuffer.append((String) cookie.get(cookieName));
                if (cookieNames.hasNext())
                {
                    cookieStringBuffer.append(SET_COOKIE_SEPARATOR);
                }
            }
        }
        try
        {
            conn.setRequestProperty(COOKIE, cookieStringBuffer.toString());
        }
        catch (IllegalStateException e)
        {
            throw new IOException("Illegal State! Cookies cannot be set on a URLConnection that is already connected. "
                    + "Only call setCookies(java.net.URLConnection) AFTER calling java.net.URLConnection.connect().");
        }
    }

    public static String getCookieDomainFromHost(String host)
    {
        while (host.indexOf(DOT) != host.lastIndexOf(DOT))
        {
            host = host.substring(host.indexOf(DOT) + 1);
        }
        return host;
    }

    static boolean isNotExpired(String cookieExpires)
    {
        if (cookieExpires == null)
        {
            return true;
        }

        try
        {
            return new Date().compareTo(DATE_FORMAT.parse(cookieExpires)) <= 0;
        }
        catch (ParseException e)
        {
            LOG.info("Parse error on cookie expires value: " + cookieExpires, e);
            return false;
        }
    }

    static boolean comparePaths(String cookiePath, String targetPath)
    {
        return cookiePath == null || "/".equals(cookiePath) || targetPath.regionMatches(0, cookiePath, 0, cookiePath.length());
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * String.
     *
     * @param url URL to hit
     * @return UTF-8 String read from URL or null in the case of error.
     */
    public static String getContentFromUrlAsString(String url)
    {
        return getContentFromUrlAsString(url, null, null, true);
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * String.
     *
     * @param url URL to hit
     * @param allowAllCerts true to not verify certificates
     * @return UTF-8 String read from URL or null in the case of error.
     */
    public static String getContentFromUrlAsString(URL url, boolean allowAllCerts)
    {
        return getContentFromUrlAsString(url, null, null, allowAllCerts);
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * String.
     *
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param trustAllCerts if true, SSL connection will always be trusted.
     * @return String of content fetched from URL.
     */
    public static String getContentFromUrlAsString(String url, Map inCookies, Map outCookies, boolean trustAllCerts)
    {
        byte[] bytes = getContentFromUrl(url, inCookies, outCookies, trustAllCerts);
        return bytes == null ? null : StringUtilities.createString(bytes, "UTF-8");
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * String.
     *
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param trustAllCerts if true, SSL connection will always be trusted.
     * @return String of content fetched from URL.
     */
    public static String getContentFromUrlAsString(URL url, Map inCookies, Map outCookies, boolean trustAllCerts)
    {
        byte[] bytes = getContentFromUrl(url, inCookies, outCookies, trustAllCerts);
        return bytes == null ? null : StringUtilities.createString(bytes, "UTF-8");
    }


    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @return byte[] read from URL or null in the case of error.
     */
    public static byte[] getContentFromUrl(String url)
    {
        return getContentFromUrl(url, null, null, true);
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @return byte[] read from URL or null in the case of error.
     */
    public static byte[] getContentFromUrl(URL url, boolean allowAllCerts)
    {
        return getContentFromUrl(url, null, null, allowAllCerts);
    }

    /*
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param ignoreSec if true, SSL connection will always be trusted.
     * @return byte[] of content fetched from URL.
     */
    public static byte[] getContentFromUrl(String url, Map inCookies, Map outCookies, boolean allowAllCerts)
    {
        try {
            return getContentFromUrl(getActualUrl(url),inCookies, outCookies, allowAllCerts);
        } catch (Exception e) {
            LOG.warn("Exception occurred fetching content from url: " + url, e);
            return null;
        }
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param allowAllCerts override certificate validation?
     * @return byte[] of content fetched from URL.
     */
    public static byte[] getContentFromUrl(URL url, Map inCookies, Map outCookies, boolean allowAllCerts)
    {
        URLConnection c = null;
        try
        {
            c = getConnection(url, inCookies, true, false, false, allowAllCerts);

            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            InputStream stream = IOUtilities.getInputStream(c);
            IOUtilities.transfer(stream, out);
            stream.close();

            if (outCookies != null)
            {   // [optional] Fetch cookies from server and update outCookie Map (pick up JSESSIONID, other headers)
                getCookies(c, outCookies);
            }

            return out.toByteArray();
        }
        catch (SSLHandshakeException e)
        {   // Don't read error response.  it will just cause another exception.
            LOG.warn("SSL Exception occurred fetching content from url: " + url, e);
            return null;
        }
        catch (Exception e)
        {
            readErrorResponse(c);
            LOG.warn("Exception occurred fetching content from url: " + url, e);
            return null;
        }
        finally
        {
            if (c instanceof HttpURLConnection)
            {
                disconnect((HttpURLConnection)c);
            }
        }
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @return byte[] of content fetched from URL.
     */
    public static byte[] getContentFromUrl(String url, Map inCookies, Map outCookies)
    {
        return getContentFromUrl(url, inCookies, outCookies, true);
    }


    /**
     * @param input boolean indicating whether this connection will be used for input
     * @param output boolean indicating whether this connection will be used for output
     * @param cache boolean allow caching (be careful setting this to true for non-static retrievals).
     * @return URLConnection established URL connection.
     */
    public static URLConnection getConnection(String url, boolean input, boolean output, boolean cache) throws IOException
    {
        return getConnection(getActualUrl(url), null, input, output, cache, true);
    }

    /**
     *
     * @param input boolean indicating whether this connection will be used for input
     * @param output boolean indicating whether this connection will be used for output
     * @param cache boolean allow caching (be careful setting this to true for non-static retrievals).
     * @return URLConnection established URL connection.
     */
    public static URLConnection getConnection(URL url, boolean input, boolean output, boolean cache) throws IOException
    {
        return getConnection(url, null, input, output, cache, true);
    }

    /**
     *  Gets a connection from a url.  All getConnection calls should go through this code.
     * @param inCookies Supply cookie Map (received from prior setCookies calls from server)
     * @param input boolean indicating whether this connection will be used for input
     * @param output boolean indicating whether this connection will be used for output
     * @param cache boolean allow caching (be careful setting this to true for non-static retrievals).
     * @return URLConnection established URL connection.
     */
    public static URLConnection getConnection(URL url, Map inCookies, boolean input, boolean output, boolean cache, boolean allowAllCerts) throws IOException
    {
        URLConnection c = url.openConnection();
        c.setRequestProperty("Accept-Encoding", "gzip, deflate");
        c.setAllowUserInteraction(false);
        c.setDoOutput(output);
        c.setDoInput(input);
        c.setUseCaches(cache);
        c.setReadTimeout(220000);
        c.setConnectTimeout(45000);

        String ref = getReferrer();
        if (StringUtilities.hasContent(ref))
        {
            c.setRequestProperty("Referer", ref);
        }
        String agent = getUserAgent();
        if (StringUtilities.hasContent(agent))
        {
            c.setRequestProperty("User-Agent", agent);
        }

        if (c instanceof HttpURLConnection)
        {   // setFollowRedirects is a static (global) method / setting - resetting it in case other code changed it?
            HttpURLConnection.setFollowRedirects(true);
        }

        if (c instanceof HttpsURLConnection && allowAllCerts)
        {
            try
            {
                setNaiveSSLSocketFactory((HttpsURLConnection) c);
            }
            catch(Exception e)
            {
                LOG.warn("Could not access '" + url.toString() + "'", e);
            }
        }

        // Set cookies in the HTTP header
        if (inCookies != null)
        {   // [optional] place cookies (JSESSIONID) into HTTP headers
            setCookies(c, inCookies);
        }
        return c;
    }

    private static void setNaiveSSLSocketFactory(HttpsURLConnection sc)
    {
        sc.setSSLSocketFactory(naiveSSLSocketFactory);
        sc.setHostnameVerifier(NAIVE_VERIFIER);
    }

    public static URL getActualUrl(String url) throws MalformedURLException
    {
        Matcher m = resPattern.matcher(url);
        return m.find() ? UrlUtilities.class.getClassLoader().getResource(url.substring(m.end())) : new URL(url);
    }

    /************************************ DEPRECATED ITEMS ONLY BELOW ******************************************/

    /**
     *
     * @return String host name
     * @deprecated As of release 1.13.0, replaced by {@link com.zcb.firstMvn.util.InetAddressUtilities#getHostName()}
     */
    @Deprecated
    public static String getHostName()
    {
        return InetAddressUtilities.getHostName();
    }

    /**
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @deprecated As of release 1.13.0, replaced by {@link #getConnection(java.net.URL, java.util.Map, boolean, boolean, boolean, boolean)}
     */
    @Deprecated
    public static URLConnection getConnection(URL url, Map inCookies, boolean input, boolean output, boolean cache, Proxy proxy, boolean allowAllCerts) throws IOException
    {
        return getConnection(url, inCookies, input, output, cache, allowAllCerts);
    }

    /**
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     *
     * @deprecated As of release 1.13.0, replaced by {@link #getConnection(java.net.URL, java.util.Map, boolean, boolean, boolean, boolean)}
     */
    @Deprecated
    public static URLConnection getConnection(URL url, String server, int port, Map inCookies, boolean input, boolean output, boolean cache, boolean allowAllCerts) throws IOException
    {
        return getConnection(url, inCookies, input, output, cache, allowAllCerts);
    }

    /**
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @deprecated As of release 1.13.0, replaced by {@link #getConnection(java.net.URL, java.util.Map, boolean, boolean, boolean, boolean)}
     */
    @Deprecated
    public static URLConnection getConnection(URL url, Map inCookies, boolean input, boolean output, boolean cache, Proxy proxy, SSLSocketFactory factory, HostnameVerifier verifier) throws IOException
    {
        return getConnection(url, inCookies, input, output, cache, true);
    }


    /**
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @param proxy proxy to use to create connection
     * @return byte[] read from URL or null in the case of error.
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrl(String)}
     */
    @Deprecated
    public static byte[] getContentFromUrl(String url, Proxy proxy)
    {
        return getContentFromUrl(url);
    }

    /**
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     * @param url URL to hit
     * @param proxy Proxy server to create connection (or null if not needed)
     * @param factory custom SSLSocket factory (or null if not needed)
     * @param verifier custom Hostnameverifier (or null if not needed)
     * @return byte[] of content fetched from URL.
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrl(String)}
     */
    @Deprecated
    public static byte[] getContentFromUrl(String url, Proxy proxy, SSLSocketFactory factory, HostnameVerifier verifier)
    {
        return getContentFromUrl(url);
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @param url URL to hit
     * @param proxy Proxy server to create connection (or null if not needed)
     * @param factory custom SSLSocket factory (or null if not needed)
     * @param verifier custom Hostnameverifier (or null if not needed)
     * @return byte[] of content fetched from URL.
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrl(String)}
     */
    @Deprecated
    public static byte[] getContentFromUrl(String url, Map inCookies, Map outCookies, Proxy proxy, SSLSocketFactory factory, HostnameVerifier verifier)
    {
        return getContentFromUrl(url, inCookies, outCookies, true);
    }




    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @param url URL to hit
     * @param proxy proxy to use to create connection
     * @return String read from URL or null in the case of error.
     *
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrl(String)}
     */
    @Deprecated
    public static String getContentFromUrlAsString(String url, Proxy proxy)
    {
        byte[] bytes = getContentFromUrl(url, proxy);
        return bytes == null ? null : StringUtilities.createString(bytes, "UTF-8");
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param proxy Proxy server to create connection (or null if not needed)
     * @return byte[] of content fetched from URL.
     *
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrl(String, java.util.Map, java.util.Map, boolean)}
     */
    @Deprecated
    public static byte[] getContentFromUrl(URL url, Map inCookies, Map outCookies, Proxy proxy, boolean allowAllCerts)
    {
        return getContentFromUrl(url, inCookies, outCookies, allowAllCerts);
    }

    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @param url URL to hit
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param proxy Proxy server to create connection (or null if not needed)
     * @return byte[] of content fetched from URL.
     *
     * @deprecated As of release 1.13.0, replaced by {@link #getConnection(String, boolean, boolean, boolean)}
     */
    @Deprecated
    public static byte[] getContentFromUrl(String url, Map inCookies, Map outCookies, Proxy proxy, boolean allowAllCerts)
    {
        try
        {
            return getContentFromUrl(getActualUrl(url), inCookies, outCookies, proxy, allowAllCerts);
        } catch (MalformedURLException e) {
            LOG.warn("Exception occurred fetching content from url: " + url, e);
            return null;
        }
    }


    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * byte[].
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     *  http.nonProxyHosts (should always include localhost)
     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @param url URL to hit
     * @param proxyServer String named of proxy server
     * @param port port to access proxy server
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param allowAllCerts if true, SSL connection will always be trusted.
     * @return byte[] of content fetched from URL.
     *
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrl(String, java.util.Map, java.util.Map, boolean)}
     */
    @Deprecated
    public static byte[] getContentFromUrl(String url, String proxyServer, int port, Map inCookies, Map outCookies, boolean allowAllCerts)
    {
        //  if proxy server is passed
        Proxy proxy = null;
        if (proxyServer != null)
        {
            proxy = new Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(proxyServer, port));
        }

        return getContentFromUrl(url, inCookies, outCookies, proxy, allowAllCerts);
    }


    /**
     * Get content from the passed in URL.  This code will open a connection to
     * the passed in server, fetch the requested content, and return it as a
     * String.
     *
     *  Anyone using the proxy calls such as this one should have that managed by the jvm with -D parameters:
     *  http.proxyHost
     *  http.proxyPort (default: 80)
     always     *  https.proxyHost
     *  https.proxyPort
     *
     *  Example:  -Dhttp.proxyHost=proxy.example.org -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.example.org -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=*.foo.com|localhost|*.td.afg
     * @param url URL to hit
     * @param proxyServer String named of proxy server
     * @param port port to access proxy server
     * @param inCookies Map of session cookies (or null if not needed)
     * @param outCookies Map of session cookies (or null if not needed)
     * @param ignoreSec if true, SSL connection will always be trusted.
     * @return String of content fetched from URL.
     *
     * @deprecated As of release 1.13.0, replaced by {@link #getContentFromUrlAsString(String, java.util.Map, java.util.Map, boolean)}
     */
    @Deprecated
    public static String getContentFromUrlAsString(String url, String proxyServer, int port, Map inCookies, Map outCookies, boolean ignoreSec)
    {
        return getContentFromUrlAsString(url, inCookies, outCookies, ignoreSec);
    }


}
