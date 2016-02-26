<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>测试</title>
</head>
<body style="background-color: green;">
 <div>
   <h1 style="background-color: fuchsia;" align="center">第一个maven程序，整合spring+springMvc+myBatis</h1>
 </div>
 <div align="center">
    <table style="height: 300px;width: 800px;">
       <tr>
         <td><span>用户名：</span> </td>
         <td><input type="text" name="account" value="${user.userName}"/> </td>
       </tr>
       <tr>
         <td><span>密码：</span> </td>
         <td><input type="password" name="account" value="${user.password}"/> </td>
       </tr>
       <tr>
         <td colspan="2" align="center">
           <input type="submit" value="login"/>
         </td>
       </tr>
    </table>
 </div>
 ${user.userName}  
</body>
</html>