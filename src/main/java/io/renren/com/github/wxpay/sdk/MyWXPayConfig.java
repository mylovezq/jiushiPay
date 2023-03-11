package io.renren.com.github.wxpay.sdk;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;

@Component
public class MyWXPayConfig extends WXPayConfig {
    @Value("${application.wxpay.app-id}")
    private String appId;

    @Value("${application.wxpay.mch-id}")
    private String mchId;

    @Value("${application.wxpay.keyV3}")
    private String keyV3;

    @Value("${application.wxpay.cert-path:''}")
    private String certPath;

    private byte[] certData;

    @PostConstruct
    public void init() throws Exception{
//        InputStream resourceAsStream = MyWXPayConfig.class.getResourceAsStream(certPath);
//        byte[] bytes = IOUtils.toByteArray(resourceAsStream,2048L);
//        this.certData=bytes;

    }

    @Override
    String getAppID() {
        return appId;
    }

    @Override
    String getMchID() {
        return mchId;
    }

    @Override
    String getKey() {
        return keyV3;
    }

    @Override
    InputStream getCertStream() {
        ByteArrayInputStream in=new ByteArrayInputStream(this.certData);
        return in;
    }

    @Override
    IWXPayDomain getWXPayDomain() {
        return new IWXPayDomain() {
            @Override
            public void report(String domain, long elapsedTimeMillis, Exception ex) {

            }

            @Override
            public DomainInfo getDomain(WXPayConfig config) {
                return new IWXPayDomain.DomainInfo(WXPayConstants.DOMAIN_API,true);
            }
        };
    }
}
