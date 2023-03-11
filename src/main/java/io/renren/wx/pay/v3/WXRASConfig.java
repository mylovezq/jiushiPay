package io.renren.wx.pay.v3;

import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.util.IOUtil;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

@Configuration
@Component
public class WXRASConfig {


    @Value("${application.wxpay.app-id}")
    private String appId;

    @Value("${application.wxpay.mch-id}")
    private String merchantId;

    @Value("${application.wxpay.keyV3}")
    private String keyV3;

    @Value("${application.wxpay.certPem}")
    private String certPem;

    @Value("${application.wxpay.privateKey}")
    private String privateKey;

    @SneakyThrows
    @Bean
    public Config config () {

        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKey(privateKey())
                .merchantSerialNumber(getCertificate())
                .apiV3Key(keyV3)
                .build();

    }


    @SneakyThrows
    @Bean
    public CertificatesVerifier certificatesVerifier () {
        CertificatesVerifier certificatesVerifier = new CertificatesVerifier(Arrays.asList(x509Certificate ()));
        return certificatesVerifier;

    }

    @SneakyThrows
    @Bean
    public X509Certificate x509Certificate () {
        InputStream resourceAsStream = WXRASConfig.class.getResourceAsStream(certPem);
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(resourceAsStream);
        return cert;

    }


    @Bean
    public  PrivateKey privateKey() throws IOException {

        InputStream resourceAsStream = WXRASConfig.class.getResourceAsStream(privateKey);
        String content = new String(IOUtil.toByteArray(resourceAsStream), "utf-8");
        try {
            String privateKey = content.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(privateKey)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持RSA", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("无效的密钥格式");
        }
    }



    @SneakyThrows
    public String getCertificate(){
        try {
            InputStream resourceAsStream = WXRASConfig.class.getResourceAsStream(certPem);
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(resourceAsStream);
            cert.checkValidity();
            return cert.getSerialNumber().toString(16);
        } catch (CertificateExpiredException e) {
            throw new RuntimeException("证书已过期", e);
        } catch (CertificateNotYetValidException e) {
            throw new RuntimeException("证书尚未生效", e);
        } catch (CertificateException e) {
            throw new RuntimeException("无效的证书", e);
        }
    }




}
