package com.cachens.nexusdashboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

final class LegacyTls {
    private static SSLSocketFactory socketFactory;

    private LegacyTls() {
    }

    static synchronized void initialize(Context context) {
        if (socketFactory != null || Build.VERSION.SDK_INT >= 21) {
            return;
        }
        try {
            java.security.Provider provider = Conscrypt.newProvider();
            Security.insertProviderAt(provider, 1);
            X509TrustManager systemTrust = trustManager(null);
            KeyStore bundledStore = KeyStore.getInstance(KeyStore.getDefaultType());
            bundledStore.load(null);
            addCertificate(context, bundledStore, "isrg-root-x1", R.raw.isrg_root_x1);
            addCertificate(context, bundledStore, "digicert-global-root-g2", R.raw.digicert_global_root_g2);
            addCertificate(context, bundledStore, "globalsign-root-r1", R.raw.globalsign_root_r1);
            X509TrustManager bundledTrust = trustManager(bundledStore);

            SSLContext sslContext = SSLContext.getInstance("TLS", provider);
            sslContext.init(null, new TrustManager[] {
                    new CompositeTrustManager(systemTrust, bundledTrust)
            }, new SecureRandom());
            socketFactory = new Tls12SocketFactory(sslContext.getSocketFactory());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to initialize TLS 1.2", error);
        }
    }

    private static void addCertificate(Context context, KeyStore keyStore, String alias, int resourceId)
            throws Exception {
        InputStream stream = context.getResources().openRawResource(resourceId);
        try {
            Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(stream);
            keyStore.setCertificateEntry(alias, certificate);
        } finally {
            stream.close();
        }
    }

    static void configure(HttpURLConnection connection) {
        if (socketFactory != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
        }
    }

    private static X509TrustManager trustManager(KeyStore keyStore) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No X509 trust manager available");
    }

    @SuppressLint("CustomX509TrustManager")
    private static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager[] delegates;

        CompositeTrustManager(X509TrustManager... delegates) {
            this.delegates = delegates;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            check(chain, authType, true);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            check(chain, authType, false);
        }

        private void check(X509Certificate[] chain, String authType, boolean client)
                throws java.security.cert.CertificateException {
            java.security.cert.CertificateException last = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    if (client) {
                        delegate.checkClientTrusted(chain, authType);
                    } else {
                        delegate.checkServerTrusted(chain, authType);
                    }
                    return;
                } catch (java.security.cert.CertificateException error) {
                    last = error;
                }
            }
            throw last == null
                    ? new java.security.cert.CertificateException("Certificate chain rejected")
                    : last;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> certificates = new ArrayList<X509Certificate>();
            for (X509TrustManager delegate : delegates) {
                X509Certificate[] accepted = delegate.getAcceptedIssuers();
                for (X509Certificate certificate : accepted) {
                    certificates.add(certificate);
                }
            }
            return certificates.toArray(new X509Certificate[certificates.size()]);
        }
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        Tls12SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        private Socket enable(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1.2"});
            }
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return enable(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return enable(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return enable(delegate.createSocket(address, port, localAddress, localPort));
        }
    }
}
