package com.dataiku.wt1.controllers;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.ProcessingQueue.QueueHandler;

@SuppressWarnings("serial")
public class HandlersServlet extends HttpServlet {
   
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (req.getPathInfo() == null) {
            resp.setStatus(400);
            resp.getWriter().write("Expected query: handlers/handlerName/handlerQueryDetails");
            return;
        }
        
        String pathInfo = req.getPathInfo();
        for (QueueHandler qh : ProcessingQueue.getInstance().getQueueHandlers()) {
            if (pathInfo.startsWith("/" + qh.getName())) {
                qh.getProcessor().service(req,  resp);
                return;
            }
        }
        resp.setStatus(404);
        resp.getWriter().write("No handler matched for " + pathInfo);
        return;
    }
}