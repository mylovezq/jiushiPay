/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package io.renren.modules.app.service;


import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.app.entity.OrderEntity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 用户
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface OrderService extends IService<OrderEntity> {

	public ArrayList<OrderEntity> searchUserOrderList(HashMap map);
}
