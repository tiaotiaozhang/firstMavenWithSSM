package com.zcb.firstMvn.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.zcb.firstMvn.model.User;

@Controller
public class LoginController {
    User user = new User();
    @RequestMapping(value="/login")  
    public String login( Model model){  
        model.addAttribute("user", user);
        return "login";  
    }  
}
