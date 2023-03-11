/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 * <p>
 * https://www.renren.io
 * <p>
 * 版权所有，侵权必究！
 */

package io.renren.modules.app.controller;


import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.SignatureResult;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.core.util.NonceUtil;

import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import io.renren.com.github.wxpay.sdk.MyWXPayConfig;
import io.renren.com.github.wxpay.sdk.WXPay;
import io.renren.com.github.wxpay.sdk.WXPayUtil;
import io.renren.common.utils.R;
import io.renren.common.validator.ValidatorUtils;
import io.renren.modules.app.annotation.Login;
//import io.renren.modules.app.entity.OrderEntity;
import io.renren.modules.app.entity.OrderEntity;
import io.renren.modules.app.entity.UserEntity;
import io.renren.modules.app.form.PayOrderForm;
import io.renren.modules.app.form.UpdateOrderStatusForm;
import io.renren.modules.app.form.WxLoginForm;
//import io.renren.modules.app.service.OrderService;
import io.renren.modules.app.service.OrderService;
import io.renren.modules.app.service.UserService;
import io.renren.modules.app.utils.JwtUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;


import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * APP登录授权
 *
 * @author Mark sunlightcs@gmail.com
 */
@RestController
@RequestMapping("/app/wx")
@Slf4j
@Api("微信业务接口")
public class WxController {
    @Value("${application.wxpay.app-id}")
    private String appId;

    @Value("${application.wxpay.app-secret}")
    private String appSecret;

    @Value("${application.wxpay.keyV3}")
    private String keyV3;

    @Value("${application.wxpay.mch-id}")
    private String mchId;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private MyWXPayConfig myWXPayConfig;
    @Autowired
    private Config config;

    @Autowired
    private CertificatesVerifier verifier;
    @Autowired
    private PrivateKey privateKey;
    @Autowired
    private X509Certificate x509Certificate;

    /**
     * 登录
     */
    @PostMapping("login")
    @ApiOperation("登录")
    public R login(@RequestBody WxLoginForm form) {
        //表单校验
        ValidatorUtils.validateEntity(form);
        String url = "https://api.weixin.qq.com/sns/jscode2session";
        HashMap map = new HashMap();
        map.put("appid", appId);
        map.put("secret", appSecret);
        map.put("js_code", form.getCode());
        map.put("grant_type", "authorization_code");
        String response = HttpUtil.post(url, map);
        cn.hutool.json.JSONObject json = JSONUtil.parseObj(response);
        String openId = json.getStr("openid");
        if (openId == null || openId.length() == 0) {
            return R.error("临时登陆凭证错误");
        }
        UserEntity user = new UserEntity();
        user.setOpenId(openId);
        QueryWrapper wrapper = new QueryWrapper(user);
        int count = userService.count(wrapper);
        if (count == 0) {
            user.setNickname(form.getNickname());
            user.setPhoto(form.getPhoto());
            user.setType(2);
            user.setCreateTime(new Date());
            userService.save(user);
        }
        user = new UserEntity();
        user.setOpenId(openId);
        wrapper = new QueryWrapper(user);
        user = userService.getOne(wrapper);
        long id = user.getUserId();

//        //用户登录
//        long userId = userService.login(form);
//
//        //生成token
        String token = jwtUtils.generateToken(id);
//
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("expire", jwtUtils.getExpire());

        return R.ok(result);
    }

    @Login
    @PostMapping("/microAppPayOrder")
    @ApiOperation("小程序付款")
    public R microAppPayOrder(@RequestBody PayOrderForm form, @RequestHeader HashMap header) {


        ValidatorUtils.validateEntity(form);
        String token = header.get("token").toString();
        Long userId = Long.parseLong(jwtUtils.getClaimByToken(token).getSubject());
        int orderId = form.getOrderId();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        QueryWrapper wrapper = new QueryWrapper(user);
        long count = userService.count(wrapper);
        if (count == 0) {
            return R.error("用户不存在");
        }
        String openId = userService.getOne(wrapper).getOpenId();

        OrderEntity order = new OrderEntity();
        order.setUserId(userId.intValue());
        order.setId(orderId);
        order.setStatus(1);
        wrapper = new QueryWrapper(order);
        count = orderService.count(wrapper);
        if (count == 0) {
            return R.error("不是有效的订单");
        }
        //验证购物券是否有效
        //验证团购活动是否有效

        order = new OrderEntity();
        order.setId(orderId);
        wrapper = new QueryWrapper(order);
        order = orderService.getOne(wrapper);
        //向微信平台发出请求，创建支付订单
        Integer amountInt = order.getAmount().multiply(new BigDecimal("100")).intValue();


        //sdk  微信支付v3封装参数
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(amountInt);
        request.setAmount(amount);
        request.setAppid(appId);
        request.setMchid(mchId);
        request.setDescription("测试商品标题");
        request.setNotifyUrl("https://2092u8x490.yicp.fun/renren-fast/app/wx/recieveMessage");

        request.setOutTradeNo(order.getCode());
        Payer payer = new Payer();
        payer.setOpenid(openId);
        request.setPayer(payer);
        JsapiService service = new JsapiService.Builder().config(config).build();

        //发起请求  得到预支付id
        String prepayId = service.prepay(request).getPrepayId();

        //参考扩展Jsapi
        long timestamp = Instant.now().getEpochSecond();
        //封装签名体
        String nonceStr = NonceUtil.createNonce(32);
        String packageVal = "prepay_id=" + prepayId;
        String message =
                request.getAppid() + "\n" + timestamp + "\n" + nonceStr + "\n" + packageVal + "\n";
        log.debug("Message for RequestPayment signatures is[{}]", message);
        //配置的私钥签名
        String sign = config.createSigner().sign(message).getSign();

        HashMap hashMap = new HashMap();
        hashMap.put("appId", request.getAppid());
        hashMap.put("timeStamp", timestamp + "");
        hashMap.put("nonceStr", nonceStr);
        hashMap.put("package", packageVal);
        hashMap.put("signType", "SHA256withRSA");
        hashMap.put("paySign", sign);

        return R.ok(hashMap);


    }


    @ApiOperation("接收消息通知")
    @RequestMapping("/recieveMessage")
    public R recieveMessage(HttpServletRequest request) throws Exception {
       //获取报文
        String body = getRequestBody(request);
        //随机串
        String nonceStr = request.getHeader("Wechatpay-Nonce");
        //微信传递过来的签名
        String signature = request.getHeader("Wechatpay-Signature");
        //证书序列号（微信平台）
        String serialNo = request.getHeader("Wechatpay-Serial");
        //时间戳
        String timestamp = request.getHeader("Wechatpay-Timestamp");
       // 构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serialNo)
                .nonce(nonceStr)
                .signature(signature)
                .timestamp(timestamp)
      // 若未设置signType，默认值为 WECHATPAY2-SHA256-RSA2048
                //.signType("SHA256withRSA")
                .body(body)
                .build();


       // 初始化 NotificationParser
        NotificationParser parser = new NotificationParser((NotificationConfig) config);

       // 验签并解密报文
        Object decryptObject = parser.parse(requestParam, Object.class);
        log.info("decryptObject*/*****************{}",decryptObject);
        return null;
    }

    @ApiOperation("接收消息通知")
    @RequestMapping("/refundMessage")
    public R refundMessage(HttpServletRequest request) throws Exception {
       //获取报文
        String body = getRequestBody(request);
        //随机串
        String nonceStr = request.getHeader("Wechatpay-Nonce");
        //微信传递过来的签名
        String signature = request.getHeader("Wechatpay-Signature");
        //证书序列号（微信平台）
        String serialNo = request.getHeader("Wechatpay-Serial");
        //时间戳
        String timestamp = request.getHeader("Wechatpay-Timestamp");
       // 构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serialNo)
                .nonce(nonceStr)
                .signature(signature)
                .timestamp(timestamp)
      // 若未设置signType，默认值为 WECHATPAY2-SHA256-RSA2048
                //.signType("SHA256withRSA")
                .body(body)
                .build();


       // 初始化 NotificationParser
        NotificationParser parser = new NotificationParser((NotificationConfig) config);

       // 验签并解密报文
        Object decryptObject = parser.parse(requestParam, Object.class);
        log.info("decryptObject*/*****************{}",decryptObject);
        return null;
    }

    /**
     * 读取请求数据流
     *
     * @param request
     * @return
     */
    private String getRequestBody(HttpServletRequest request) {

        StringBuffer sb = new StringBuffer();

        try (ServletInputStream inputStream = request.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            log.error("读取数据流异常:{}", e);
        }
        return sb.toString();

    }


    @Login
    @PostMapping("/updateOrderStatus")
    @ApiOperation("更新商品订单状态")
    public R updateOrderStatus(@RequestBody UpdateOrderStatusForm form,
                               @RequestHeader HashMap header) {
        ValidatorUtils.validateEntity(form);
        String token = header.get("token").toString();
        int userId = Integer.parseInt(jwtUtils.getClaimByToken(token).getSubject());
        int orderId = form.getOrderId();
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(userId);
        orderEntity.setId(orderId);
        QueryWrapper wrapper = new QueryWrapper(orderEntity);
        int count = orderService.count(wrapper);
        if (count == 0) {
            return R.error("用户与订单不匹配");
        }
        orderEntity = orderService.getOne(wrapper);
        String code = orderEntity.getCode();

        QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
        queryRequest.setMchid(mchId);
        queryRequest.setOutTradeNo(orderEntity.getCode());
        JsapiService service = new JsapiService.Builder().config(config).build();
        try {
            Transaction result = service.queryOrderByOutTradeNo(queryRequest);
            System.out.println(result.getTradeState());
        } catch (ServiceException e) {
            // API返回失败, 例如ORDER_NOT_EXISTS
            System.out.printf("code=[%s], message=[%s]\n", e.getErrorCode(), e.getErrorMessage());
            System.out.printf("reponse body=[%s]\n", e.getResponseBody());
        }


        return null;
    }

    @PostMapping("/microAppRefundOrder")
    @ApiOperation("小程序付款")
    public R refund() {
        RefundService refundService = new RefundService.Builder().config(config).build();
        CreateRequest createRequest = new CreateRequest();
        createRequest.setOutTradeNo("CX0000000999991");
        createRequest.setReason("没有货了");
        AmountReq amount = new AmountReq();
        amount.setRefund(1L);
        amount.setTotal(1L);
        amount.setCurrency("CNY");
        createRequest.setNotifyUrl("https://2092u8x490.yicp.fun/renren-fast/app/wx/refundMessage");
        createRequest.setAmount(amount);
        com.wechat.pay.java.service.refund.model.GoodsDetail goodsDetail = new com.wechat.pay.java.service.refund.model.GoodsDetail();
        goodsDetail.setGoodsName("测试");
        goodsDetail.setRefundQuantity(1);
        goodsDetail.setMerchantGoodsId("1217752501201407033233368018");
        goodsDetail.setUnitPrice(100L);
        goodsDetail.setRefundAmount(100L);
        createRequest.setOutRefundNo("9999999999999999999");
        createRequest.setGoodsDetail(Arrays.asList(goodsDetail));

        Refund refund = refundService.create(createRequest);
        JSONObject jsonObject = JSONUtil.parseObj(refund);
        log.info("jsonObject**********{}",jsonObject);
        return null;
    }



//
//    @PostMapping("/nativePayOrder")
//    @ApiOperation("native付款")
//    public R nativePayOrder(@RequestBody PayOrderForm form, @RequestHeader HashMap header) {
//        ValidatorUtils.validateEntity(form);
//        String token = header.get("token").toString();
//        Long userId = Long.parseLong(jwtUtils.getClaimByToken(token).getSubject());
//        int orderId = form.getOrderId();
//        UserEntity user = new UserEntity();
//        user.setUserId(userId);
//        QueryWrapper wrapper = new QueryWrapper(user);
//        long count = userService.count(wrapper);
//        if (count == 0) {
//            return R.error("用户不存在");
//        }
//
//        OrderEntity order = new OrderEntity();
//        order.setUserId(userId.intValue());
//        order.setId(orderId);
//        order.setStatus(1);
//        wrapper = new QueryWrapper(order);
//        count = orderService.count(wrapper);
//        if (count == 0) {
//            return R.error("不是有效的订单");
//        }
//        //验证购物券是否有效
//        //验证团购活动是否有效
//
//        order = new OrderEntity();
//        order.setId(orderId);
//        wrapper = new QueryWrapper(order);
//        order = orderService.getOne(wrapper);
//        //向微信平台发出请求，创建支付订单
//        String amount = order.getAmount().multiply(new BigDecimal("100")).intValue() + "";
//
//        try {
//            WXPay wxPay = new WXPay(myWXPayConfig);
//            HashMap map = new HashMap();
//            map.put("nonce_str", WXPayUtil.generateNonceStr()); //随机字符串
//            map.put("body", "订单备注");
//            map.put("out_trade_no", order.getCode());
//            map.put("total_fee", amount);
//            map.put("spbill_create_ip", "127.0.0.1");
//            map.put("notify_url", "https://127.0.0.1/test");
//            map.put("trade_type", "NATIVE");
//            String sign=WXPayUtil.generateSignature(map,key);
//            map.put("sign",sign);
//            Map<String, String> result = wxPay.unifiedOrder(map);
//            String prepayId = result.get("prepay_id");
////            System.out.println(prepayId);
//            String codeUrl=result.get("code_url");
//            if (prepayId != null) {
//                order.setPrepayId(prepayId);
//                UpdateWrapper updateWrapper = new UpdateWrapper();
//                updateWrapper.eq("id", order.getId());
//                orderService.update(order, updateWrapper);
//                return R.ok().put("codeUrl", codeUrl);
//            } else {
//                return R.error("支付订单创建失败");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return R.error("微信支付模块故障");
//        }
//    }
//
//    @GetMapping("/qrcode")
//    public void qrcode(HttpServletRequest request,HttpServletResponse response) throws Exception{
//        String codeUrl=request.getParameter("codeUrl");
//        if(codeUrl!=null&&codeUrl.length()>0){
//            QrConfig qrConfig=new QrConfig();
//            qrConfig.setWidth(250);
//            qrConfig.setHeight(250);
//            qrConfig.setMargin(2);
//            OutputStream out=response.getOutputStream();
//            QrCodeUtil.generate(codeUrl,qrConfig,"jpg",out);
//            out.close();
//        }
//    }
//    @Login
//    @PostMapping("/searchOrderStatus")
//    @ApiOperation("查询支付订单状态")
//    public R searchOrderStatus(@RequestBody SearchOrderStatusForm form,
//                               @RequestHeader HashMap header){
//        ValidatorUtils.validateEntity(form);
//        String token = header.get("token").toString();
//        int userId = Integer.parseInt(jwtUtils.getClaimByToken(token).getSubject());
//        int orderId = form.getOrderId();
//        OrderEntity orderEntity = new OrderEntity();
//        orderEntity.setUserId(userId);
//        orderEntity.setId(orderId);
//        QueryWrapper wrapper = new QueryWrapper(orderEntity);
//        int count = orderService.count(wrapper);
//        if (count == 0) {
//            return R.error("用户与订单不匹配");
//        }
//        orderEntity = orderService.getOne(wrapper);
//        String code = orderEntity.getCode();
//        HashMap map = new HashMap();
//        map.put("appid", appId);
//        map.put("mch_id", mchId);
//        map.put("out_trade_no", code);
//        map.put("nonce_str", WXPayUtil.generateNonceStr());
//        try {
//            String sign = WXPayUtil.generateSignature(map, key);
//            map.put("sign", sign);
//            WXPay wxPay = new WXPay(myWXPayConfig);
//            Map<String, String> result = wxPay.orderQuery(map);
//            String returnCode = result.get("return_code");
//            String resultCode = result.get("result_code");
//            if ("SUCCESS".equals(returnCode) && "SUCCESS".equals(resultCode)) {
//                String tradeState = result.get("trade_state");
//                if ("SUCCESS".equals(tradeState)) {
//                    UpdateWrapper updateWrapper = new UpdateWrapper();
//                    updateWrapper.eq("code", code);
//                    updateWrapper.set("status", 2);
//                    updateWrapper.set("payment_type",1);
//                    orderService.update(updateWrapper);
//                    return R.ok("订单状态已修改");
//                } else {
//                    return R.ok("订单状态未修改");
//                }
//            }
//            return R.ok("订单状态未修改");
//        } catch (Exception e) {
//            e.printStackTrace();
//            return R.error("查询支付订单失败");
//        }
//    }
//
//    @Login
//    @PostMapping("/scanCodePayOrder")
//    @ApiOperation("付款码收款")
//    public R scanCodePayOrder(@RequestBody ScanCodePayOrderForm form, @RequestHeader HashMap header) {
//        ValidatorUtils.validateEntity(form);
//        String token = header.get("token").toString();
//        Long userId = Long.parseLong(jwtUtils.getClaimByToken(token).getSubject());
//        int orderId = form.getOrderId();
//        UserEntity user = new UserEntity();
//        user.setUserId(userId);
//        QueryWrapper wrapper = new QueryWrapper(user);
//        long count = userService.count(wrapper);
//        if (count == 0) {
//            return R.error("用户不存在");
//        }
//
//        OrderEntity order = new OrderEntity();
//        order.setUserId(userId.intValue());
//        order.setId(orderId);
//        order.setStatus(1);
//        wrapper = new QueryWrapper(order);
//        count = orderService.count(wrapper);
//        if (count == 0) {
//            return R.error("不是有效的订单");
//        }
//        //验证购物券是否有效
//        //验证团购活动是否有效
//
//        order = new OrderEntity();
//        order.setId(orderId);
//        wrapper = new QueryWrapper(order);
//        order = orderService.getOne(wrapper);
//        //向微信平台发出请求，创建支付订单
//        String amount = order.getAmount().multiply(new BigDecimal("100")).intValue() + "";
//
//        try {
//            WXPay wxPay = new WXPay(myWXPayConfig);
//            HashMap map = new HashMap();
//            map.put("appid",appId);
//            map.put("mch_id",mchId);
//            map.put("nonce_str", WXPayUtil.generateNonceStr()); //随机字符串
//            map.put("body", "订单备注");
//            map.put("out_trade_no", order.getCode());
//            map.put("total_fee", amount);
//            map.put("spbill_create_ip", "127.0.0.1");
//            map.put("auth_code",form.getAuthCode());
//            String sign=WXPayUtil.generateSignature(map,key);
//            map.put("sign",sign);
//            Map<String, String> result = wxPay.microPay(map);
//            String returnCode = result.get("return_code");
//            String resultCode = result.get("result_code");
//            if ("SUCCESS".equals(returnCode) && "SUCCESS".equals(resultCode)) {
//                String prepayId = result.get("transaction_id");
//                order.setPrepayId(prepayId);
//                order.setStatus(2);
//                order.setPaymentType(1);
//                UpdateWrapper updateWrapper = new UpdateWrapper();
//                updateWrapper.eq("id", order.getId());
//                orderService.update(order, updateWrapper);
//                return R.ok("付款成功");
//            }
//            else {
//                return R.error("付款失败");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return R.error("微信支付模块故障");
//        }


}
