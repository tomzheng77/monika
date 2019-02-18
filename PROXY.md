# /etc/environment

set http_proxy and https_proxy in /etc/environment

# IntelliJ IDEA - Proxy: Automatic

# SBT settings - VM parameters
-Djavax.net.ssl.trustStore="keystore.p12"
-Djavax.net.ssl.trustStorePassword="123456"

# curl --cacert certificate.cer

# Deprecated
https://drissamri.be/blog/2017/02/22/java-keystore-keytool-essentials/
cd /etc/pki/ca-trust/extracted/java/cacerts
keytool -importcert \
        -trustcacerts -file certificate.cer \
        -alias monika \
        -keystore cacerts

