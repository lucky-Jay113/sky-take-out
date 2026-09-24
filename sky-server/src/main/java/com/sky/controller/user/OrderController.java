package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {

        @Autowired
        private OrderService orderService;

        @PostMapping("/submit")
        @ApiOperation("用户下单")
        public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
                log.info("用户下单：{}", ordersSubmitDTO);
                OrderSubmitVO orderSubmitVO = orderService.submit(ordersSubmitDTO);
                return Result.success(orderSubmitVO);
        }

        /**
         * 订单支付
         *
         * @param ordersPaymentDTO
         * @return
         */
        @PutMapping("/payment")
        @ApiOperation("订单支付")
        public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
                log.info("订单支付：{}", ordersPaymentDTO);
                OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
                log.info("生成预支付交易单：{}", orderPaymentVO);
                return Result.success(orderPaymentVO);
        }

        @GetMapping("/historyOrders")
        @ApiOperation("查看历史订单")
        public Result<PageResult> page (int page, int pageSize, Integer status){
                log.info("查看历史订单,页码：{}，页数：{}，订单状态：{}", page, pageSize, status);
                PageResult pageResult = orderService.page(page, pageSize, status);
                return Result.success(pageResult);
        }

        /**
         * 订单详情
         * @param id
         * @return
         */
        @GetMapping("/orderDetail/{id}")
        @ApiOperation("订单详情")
        public Result<OrderVO> detail(@PathVariable Long id){
                log.info("订单详情，订单id：{}", id);
                OrderVO orderVO = orderService.detail(id);
                return Result.success(orderVO);
        }

        /**
         * 取消订单
         * @param id
         * @return
         */
        @PutMapping("/cancel/{id}")
        @ApiOperation("取消订单")
        public Result cancel(@PathVariable Long id) throws Exception {
                log.info("取消订单，订单id：{}", id);
                orderService.userCancelById(id);
                return Result.success();
        }

        /**
         * 再来一单
         * @param id
         * @return
         */
        @PostMapping("/repetition/{id}")
        @ApiOperation("再来一单")
        public Result repeatSubmit(@PathVariable Long id) {
                log.info("再来一单，订单id：{}", id);
                orderService.repeatSubmit(id);
                return Result.success();
        }

        /**
         * 用户催单
         * @param id
         * @return
         */
        @GetMapping("/reminder/{id}")
        @ApiOperation("订单催单")
        public Result reminder(@PathVariable Long id){
                log.info("订单催单，订单id：{}", id);
                orderService.reminder(id);
                return Result.success();
        }

}
