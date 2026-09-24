package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单
     * 每隔一分钟检查一次待付款状态的订单，如果超过15分钟未支付则取消该订单
     */
    @Scheduled(cron = "0 * * * * ? ")
    //@Scheduled(cron = "0/5 * * * * ?")
    public void processTimeoutOrder(){

        log.info("处理支付超时订单：{}",new Date());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        //SQL:select * from orders where status = 1 and order_time < (当前时间-15分钟)
        List<Orders> ordersList = orderMapper.getByStatusAndOrdertimeLT(Orders.PENDING_PAYMENT,time);

        //修改支付超时订单的为已取消，取消时间
        if (ordersList != null && !ordersList.isEmpty()){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("支付超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }

    }

    /**
     * 处理待派送订单
     * 在凌晨1点钟执行一次，将派送中的订单改为已完成
     */
    @Scheduled(cron = "0 0 1 * * ?")
    //@Scheduled(cron = "0/5 * * * * ?")
    public void processDeliveryOrder(){

        log.info("处理派送中的订单：{}",new Date());

        //time:上一天的12点=当前时间（凌晨1点）-60分钟
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> ordersList = orderMapper.getByStatusAndOrdertimeLT(Orders.DELIVERY_IN_PROGRESS,time);

        if (ordersList != null && !ordersList.isEmpty()){
            ordersList.forEach(orders -> {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            });
        }
    }

}
