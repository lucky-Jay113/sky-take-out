package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    //注入商家地址和百度ak的配置项
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;

    private Orders orders;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {

        //异常情况的处理（收货地址为空，购物车为空，超出配送范围）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //检查用户的收货地址是否超出配送范围
        //checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());

        ShoppingCart shoppingCart = new ShoppingCart();
        Long currentId = BaseContext.getCurrentId();
        shoppingCart.setUserId(currentId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list == null || list.isEmpty()){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //构造订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setPhone(addressBook.getPhone());
        orders.setAddress(addressBook.getDetail());
        orders.setConsignee(addressBook.getConsignee());
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setUserId(currentId);
        orders.setStatus(Orders.PENDING_PAYMENT); //待付款
        orders.setPayStatus(Orders.UN_PAID); //未支付
        orders.setOrderTime(LocalDateTime.now());

        //向订单表插入1条数据
        orderMapper.insert(orders);

        this.orders = orders;

        //订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : list){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }

        //向订单明细表插入n条数据
        orderDetailMapper.insertBatch(orderDetailList);

        //清空购物车数据
        shoppingCartMapper.deleteByUserId(currentId);

        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;

    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        /*JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code","ORDERPAID");

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        Integer OrderPaidStatus = Orders.PAID;//支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单
        LocalDateTime check_out_time = LocalDateTime.now();//更新支付时间

        orders.setPayStatus(OrderPaidStatus);
        orders.setStatus(OrderStatus);
        orders.setCheckoutTime(check_out_time);

        //用户支付成功之后，向客户端浏览器发送订单提醒
        //发送type为1表示，来单提醒，orderId订单号，content信息内容
        Map map = new HashMap();
        map.put("type",1);
        map.put("orderId",orders.getId());
        map.put("content","订单号："+orders.getNumber());

        //通过WebSocket实现来单提醒，向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

        orderMapper.update(orders);
        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 订单列表
     *
     * @param
     * @return
     */
    public PageResult page(int pageNum, int pageSize, Integer status) {
        //开始分页
        PageHelper.startPage(pageNum, pageSize);
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        //分页查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        //查询订单明细，并封装OrderVo
        if (page != null && page.size() > 0) {
            for(Orders orders : page){

                Long orderId = orders.getId();

                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);

                orderVOList.add(orderVO);
            }

        }

        return new PageResult(page.getTotal(), orderVOList);

    }

    /**
     * 订单详情
     *
     * @param id
     * @return
     */
    public OrderVO detail(Long id) {

        orders = orderMapper.getById(id);
        Long ordersId = orders.getId();
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(ordersId);
        orderVO.setOrderDetailList(orderDetailList);

        String orderDishes = getOrderDishes(ordersId);
        orderVO.setOrderDishes(orderDishes);

        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    public void userCancelById(Long id) throws Exception {

        Orders orderDB = orderMapper.getById(id);

        //校验订单是否存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3待派送 4派送中 5已完成 6已取消
        if (orderDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

        //订单处于待接单状态下需要进行退款
        if (orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {

            //利用微信支付接口退款
//            weChatPayUtil.refund(
//                    orderDB.getNumber(),
//                    orderDB.getNumber(),
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额

            //支付状态改为已退款
            orders.setPayStatus(Orders.REFUND);
        }


        //更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(MessageConstant.ORDER_CANCEL);
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);


    }

    /**
     * 再来一单
     *
     * @param id
     */
    public void repeatSubmit(Long id) {

        //将原来的订单重新加入到购物车当中

        //获取原来的订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {

            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCartList.add(shoppingCart);

        }


        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    /**
     * 订单提醒
     *
     * @param id
     */
    public void reminder(Long id) {

        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //向客户端浏览器发送催单消息
        Map map = new HashMap();
        map.put("type",2);
        map.put("orderId",orderDB.getId());
        map.put("content","订单号："+orderDB.getNumber());

        //通过WebSocket实现催单提醒，向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {

        //开始分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //部分订单状态，需额外返回菜品信息，将orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);

    }


    private List<OrderVO> getOrderVOList(Page<Orders> page){
        //需要返回订单菜品信息，自定义OrderVo响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        //查询订单明细，并封装OrderVo
        if (ordersList != null && !ordersList.isEmpty()) {
            for(Orders orders : ordersList){

                Long orderId = orders.getId();

                OrderVO orderVO = new OrderVO();
                //复制重复的属性
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishes(orderId);

                //将菜品数据封装到orderVO
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }

        }
        return orderVOList;


    }

    private String getOrderDishes(Long orderId){
        //查询每一个订单中的菜品数据
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);

        //将每一条菜品数据转化成字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(orderDetail -> {
            String orderDish = orderDetail.getName() + "*" + orderDetail.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        //将每个订单中的菜品数据拼接在一起
        return String.join("", orderDishList);

    }

    /**
     * 订单状态统计
     *
     * @return
     */
    public OrderStatisticsVO statistics() {

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();

        //SQL: select count(id) from orders where status = #{status}
        //待接单2 待派送3 派送中4

        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;

    }

    /**
     * 订单确认
     * @param ordersConfirmDTO
     */
    public void confirmOrder(OrdersConfirmDTO ordersConfirmDTO) {

        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

                orderMapper.update(orders);


    }

    /**
     * 订单拒绝
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {

        //根据id查询订单
        Orders orderDB = orderMapper.getById(ordersRejectionDTO.getId());

        //检验订单状态(订单为空或者状态不为待接单)
        if (orderDB == null || !orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();

        //用户已经支付
        Integer payStatus = orderDB.getPayStatus();
        if (payStatus == Orders.PAID) {

            //利用微信支付接口退款
//            weChatPayUtil.refund(
//                    orderDB.getNumber(),
//                    orderDB.getNumber(),
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额

            orders.setPayStatus(Orders.REFUND);

        }

        //拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间


        orders.setId(ordersRejectionDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);

    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) {

        //根据id查询订单
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

        Integer payStatus = orderDB.getPayStatus();
        //如果订单的支付状态为已支付，则调用微信支付接口进行退款
        if (payStatus == Orders.PAID) {
            //利用微信支付接口退款
//            weChatPayUtil.refund(
//                    orderDB.getNumber(),
//                    orderDB.getNumber(),
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额\

            orders.setPayStatus(Orders.REFUND);
        }

        //管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);

    }

    /**
     * 派送订单
     * @param id
     */
    public void delivery(Long id) {

        //根据id查询订单
        Orders orderDB = orderMapper.getById(id);

        //只有状态为“待派送”的订单才能派送
        if (orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        //更新订单状态为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     * @param id
     */
    public void completion(Long id) {

        //根据id查询订单
        Orders orderDB = orderMapper.getById(id);

        //只有状态为“派送中”的订单才能派送
        if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        //更新订单状态为已完成,送达时间
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);

    }

    /**
     * 检查用户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address){

        Map map = new HashMap();
        //商家地址
        map.put("address", shopAddress);
        map.put("output", "json");
        map.put("ak", ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        //转化为JSON对象
        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException(MessageConstant.SHOP_COORDINATE_ERROR);
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        //获取经度
        String lng = location.getString("lng");
        //获取纬度
        String lat = location.getString("lat");
        //店铺经纬度
        String shopLngLat = lng + "," + lat;

        //用户收货地址
        map.put("address",address);
        //获取店铺的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        //转化为JSON对象
        jsonObject = JSON.parseObject(userCoordinate);
        if (jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException(MessageConstant.SHOP_COORDINATE_ERROR);
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        //获取经度
        lng = location.getString("lng");
        //获取纬度
        lat = location.getString("lat");
        //店铺经纬度
        String userLngLat = lng + "," + lat;

        map.put("origin", shopLngLat);
        map.put("destination", userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/direction/v2/driving", map);

        //转化为json对象
        jsonObject = JSON.parseObject(json);
        if (jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException(MessageConstant.SHOP_COORDINATE_ERROR);
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = result.getJSONArray("routes");
        Integer  distance = (Integer) ((JSONObject)jsonArray.get(0)).get("distance");

        if (distance > 5000){
            //超过5公里
            throw new OrderBusinessException(MessageConstant.OUT_OF_DELIVERY_RANGE);
        }
    }

}
