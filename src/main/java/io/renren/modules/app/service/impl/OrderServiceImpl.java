/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package io.renren.modules.app.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.renren.modules.app.dao.OrderDao;
import io.renren.modules.app.entity.OrderEntity;
import io.renren.modules.app.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {
	@Autowired
	private OrderDao orderDao;
	public ArrayList<OrderEntity> searchUserOrderList(HashMap map){
		ArrayList<OrderEntity> list=orderDao.searchUserOrderList(map);
		return list;
	}

}
