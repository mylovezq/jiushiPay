/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package io.renren.modules.app.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.renren.common.utils.R;
import io.renren.common.validator.ValidatorUtils;
import io.renren.modules.app.annotation.Login;
import io.renren.modules.app.entity.OrderEntity;
import io.renren.modules.app.form.SearchOrderForm;
import io.renren.modules.app.form.UserOrderForm;
import io.renren.modules.app.service.OrderService;
import io.renren.modules.app.utils.JwtUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 *
 * @author Mark sunlightcs@gmail.com
 */
@RestController
@RequestMapping("/app/order")
@Api("订单业务接口")
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private JwtUtils jwtUtils;

    @Login
    @PostMapping("/searchUserOrderList")
    @ApiOperation("查询用户订单")
    public R searchUserOrderList(@RequestBody UserOrderForm form,@RequestHeader HashMap header){
        ValidatorUtils.validateEntity(form);
        String token=header.get("token").toString();
        int userId=Integer.parseInt(jwtUtils.getClaimByToken(token).getSubject());
        int page=form.getPage();
        int length=form.getLength();
        int start=(page-1)*length;
        HashMap map=new HashMap();
        map.put("userId",userId);
        map.put("start",start);
        map.put("length",length);
        ArrayList<OrderEntity> list=orderService.searchUserOrderList(map);
        return R.ok().put("list",list);
    }

    @Login
    @PostMapping("/searchOrderById")
    @ApiOperation("查询订单")
    public R searchOrderById(@RequestBody SearchOrderForm form){
        ValidatorUtils.validateEntity(form);
        QueryWrapper wrapper=new QueryWrapper();
        wrapper.eq("id",form.getOrderId());
        OrderEntity order=orderService.getOne(wrapper);
        return R.ok().put("order",order);
    }

}
