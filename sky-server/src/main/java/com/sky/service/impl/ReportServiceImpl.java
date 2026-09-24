package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        //获取日期列表
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList = new ArrayList<>();
        //获取营业额数据 select sum(amount) from order where order_time > ? and order_time < ?
        for (LocalDate date : dateList) {

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);

            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //数据封装
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();

    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        //获取日期列表
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //总用户列表
        List<Integer> totalUserList = new ArrayList<>();
        //新用户列表
        List<Integer> newUserList = new ArrayList<>();
        for (LocalDate date : dateList) {

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //获取总用户数 select count(id) from user where createTime < end

            Integer totalUserCount = userMapper.getUserCount(null,endTime);
            totalUserList.add(totalUserCount);
            //获取新增用户列表 select count(id) from user where createTime > begin and createTime < end

            Integer newUserCount = userMapper.getUserCount(beginTime,endTime);
            newUserList.add(newUserCount);
        }

        //数据封装
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();

    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        //获取日期列表
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //每日订单列表
        List<Integer> orderCountList = new ArrayList<>();
        //每日有效订单列表
        List<Integer> validOrderCountList = new ArrayList<>();

        for (LocalDate date : dateList){

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            //查询总订单数 select count(id) from orders where order_time > beginTime and order_time < endTime
            map.put("begin",beginTime);
            map.put("end",endTime);
            Integer orderCount = orderMapper.getOrderMap(map);
            orderCountList.add(orderCount);
            //查询有效订单数 select count(id) from orders where status = 5 and order_time > beginTime and order_time < endTime
            map.put("status",Orders.COMPLETED);
            Integer validOrderCount = orderMapper.getOrderMap(map);
            validOrderCountList.add(validOrderCount);
        }

        //获取总订单数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //获取有效订单总数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //订单完成率
        Double orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        //封装数据
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();

    }

    /**
     * 获取销量top10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end) {


        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        //查询top10的商品
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> nameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        //封装数据
        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();

    }

    /**
     * 导出近30天的数据
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response) throws IOException {

        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);
        //查询概览数据，提供excel模板
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        //获取excel模板的输入流
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");


        try {
            //基于提供好的模板，创建一个excel表格对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);

            //获取sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            //设置时间
            sheet.getRow(1).getCell(1).setCellValue("时间：" + begin + "至" + end);

            //获取第4行
            XSSFRow row = sheet.getRow(3);
            //获取单元格（营业额，订单完成率，新增用户数）
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());

            //获取第5行
            row = sheet.getRow(4);
            //设置单元格（有效订单，平均客单价）
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());

            //设置近30天的明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                BusinessDataVO businessDataOfOneDay = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                row = sheet.getRow(7 + i);
                //日期，营业额，有效订单，订单完成率，平均客单价，新增用户数
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessDataOfOneDay.getTurnover());
                row.getCell(3).setCellValue(businessDataOfOneDay.getValidOrderCount());
                row.getCell(4).setCellValue(businessDataOfOneDay.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessDataOfOneDay.getUnitPrice());
                row.getCell(6).setCellValue(businessDataOfOneDay.getNewUsers());

            }

            //通过输出流将文件下载到客户端中
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);

            //资源关闭
            excel.close();
            outputStream.close();
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }



    }

}
