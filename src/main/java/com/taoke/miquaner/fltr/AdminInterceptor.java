package com.taoke.miquaner.fltr;

import com.taoke.miquaner.MiquanerApplication;
import com.taoke.miquaner.ctrl.AdminCtrl;
import com.taoke.miquaner.util.Result;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class AdminInterceptor implements HandlerInterceptor {

    private static final Logger logger = LogManager.getLogger(AdminInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o) throws Exception {
        logger.info("test: pre handler. " + (o instanceof HandlerMethod));
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
        logger.info("test: post handler. " + (o instanceof HandlerMethod));
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
        logger.info("test: after completion. " + (o instanceof HandlerMethod));
    }

}