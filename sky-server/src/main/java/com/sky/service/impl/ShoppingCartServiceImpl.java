package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {

        //获取用户id
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long currentId = BaseContext.getCurrentId();
        shoppingCart.setUserId(currentId);

        //判断购物车是否存在
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //如果存在当前购物车商品数量加一
        if (list != null && list.size() == 1) {
            shoppingCart = list.get(0);
            shoppingCart.setNumber(shoppingCart.getNumber() + 1);
            shoppingCartMapper.update(shoppingCart);
        }else {
            //如果不存在，则添加到购物车当中,数量就是1
            //如果当前用户选择的商品是菜品，则购物车数据表中添加菜品数据
            Long dishId = shoppingCartDTO.getDishId();
            if (dishId != null) {
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());

            }else {
                Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }


            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());

            //如果不存在该商品就插入一条数据
            shoppingCartMapper.insert(shoppingCart);
        }

    }

    /**
     * 显示购物车
     * @return
     */
    public List<ShoppingCart> showShoppingCart() {

        Long currentId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = ShoppingCart.builder()
                .id(currentId)
                .build();

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        return list;

    }

    /**
     * 清空购物车
     */
    public void cleanShoppingCart() {

        Long currentId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(currentId);

    }

    /**
     * 删除购物车单个数据
     * @param shoppingCartDTO
     */
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {

        //获取用户id
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long currentId = BaseContext.getCurrentId();
        shoppingCart.setUserId(currentId);

        //判断购物车是否存在
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //如果存在当前购物车商品数量减一
        if (list != null && list.size() > 0) {
            shoppingCart = list.get(0);

            Integer number = shoppingCart.getNumber();

            //如果购物车中只存在一条数据，直接删除购物车
            if (number == 1) {
                shoppingCartMapper.deleteById(shoppingCart.getId());
            }else {
                //如果购物车中存在多条数据，则判断数量，如果数量为1，则删除该条数据
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.update(shoppingCart);
            }


        }

    }
}
