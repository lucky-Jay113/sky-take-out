package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 查询地址列表
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {

       return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     * @param addressBook
     */
    public void save(AddressBook addressBook) {

        Long currentId = BaseContext.getCurrentId();
        addressBook.setUserId(currentId);
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);

    }

    /**
     * 设置默认地址
     */
    public void setDefault(AddressBook addressBook) {

        //1. 将当前用户的所有地址设置非默认
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2. 将当前用户的地址改为默认
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }
    /**
     * 根据id修改地址
     * @param addressBook
     */
    public void update(AddressBook addressBook) {

        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.update(addressBook);

    }

    /**
     * 根据id查询地址
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {

        AddressBook addressBook = addressBookMapper.getById(id);
        return addressBook;
    }

    public void delete(Long id) {

        addressBookMapper.delete(id);
    }


}
