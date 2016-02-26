package com.zcb.firstMvn.service.impl;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.zcb.firstMvn.dao.UserMapper;
import com.zcb.firstMvn.model.User;
import com.zcb.firstMvn.service.UserService;

@Service("userService")
public class UserServiceImpl implements UserService {
    
    @Resource
    private UserMapper userMapper;

    @Override
    public User getUserById(int userId) {
        // TODO Auto-generated method stub
        return this.userMapper.selectByPrimaryKey(userId);
    }

}
