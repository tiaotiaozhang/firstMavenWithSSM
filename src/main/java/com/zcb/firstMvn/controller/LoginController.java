package com.zcb.firstMvn.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.zcb.firstMvn.model.User;

@Controller
public class LoginController {
    @RequestMapping(value="/login")  
    public String login( Model model){  
        User user = new User();
        user.setId(121212);
        model.addAttribute("user", user);
        return "login";  
    }  
}
