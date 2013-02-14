package com.dataiku.wt1.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.google.gson.Gson;

@SuppressWarnings("serial")
public class LiveDataServlet extends HttpServlet {
    static class LiveDataInfo {
        List<TrackedRequest> lastRequests = new ArrayList<TrackedRequest>();
        ProcessingQueue.Stats stats;
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LiveDataInfo di = new LiveDataInfo();
        ProcessingQueue.getInstance().fillLastReceived(di.lastRequests);
        
        Collections.reverse(di.lastRequests);
        
        di.stats = ProcessingQueue.getInstance().getStats();

        resp.setContentType("application/json");
        String out = null;
        synchronized (di.stats) {
            out=  new Gson().toJson(di);
        }
        resp.getWriter().write(out);
    }
}