package com.dataiku.wt1.controllers;

import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.google.gson.Gson;

@SuppressWarnings("serial")
/** JSON API tracking servlet */
public class APIServlet extends HttpServlet {
    /** The expected JSON message */
    static class Message {
        String visitorId;
        String vparams;
        String sparams;
        String ua;
        String clientLang;
        List<Event> events;
    }

    static class Event {
        String type;
        long clientTS;
        String page;
        String params;
    }

    private void send400(HttpServletRequest req, HttpServletResponse resp, String message) {
        try {
            logger.warn("Rejected API message from " + req.getRemoteAddr() + ": " + message);
            resp.setStatus(400);
            resp.getWriter().write("Bad request: " + message);
        } catch (IOException e) {
        }
    }

    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        /* Parse input, handle GZIP */
        String data = null;
        if ("application/gzip".equals(req.getContentType())) {
            try {
                data = IOUtils.toString(new GZIPInputStream(req.getInputStream()), "utf8");
            } catch (Exception e) {
                send400(req, resp, "Malformed gzipped message, " + e.getMessage());
                return;
            }
        } else if ("application/json".equals(req.getContentType())) {
            try {
                data = IOUtils.toString(req.getInputStream(), "utf8");
            } catch (Exception e) {
                send400(req, resp, "Malformed JSON message, " + e.getMessage());
                return;
            }
        } else {
            send400(req, resp, "Unexpected content type");
            return;
        }

        /* Deserialize JSON message */
        Message m = new Message();
        try {
            m = new Gson().fromJson(data, Message.class);
        } catch (Exception e) {
            send400(req, resp, "Malformed JSON message, " + e.getMessage());
            return;
        }

        if (m.events == null) {
            send400(req, resp, "Malformed JSON message, no events");
            return;
        }
        
        /* Message is OK, fill in our internal structure and enqueue */
        for (Event event : m.events) {
            TrackedRequest trackedReq = new TrackedRequest();
            trackedReq.origAddress = req.getRemoteHost();
            trackedReq.visitorCookieValue = m.visitorId;
            trackedReq.visitorParamsCookieValue = m.vparams;
            trackedReq.sessionParamsCookieValue = m.sparams;
            trackedReq.page = event.page;
            // referer
            // tzOffset
            // sizes
            trackedReq.ua = m.ua;
            trackedReq.browserLanguage = m.clientLang;
            trackedReq.type = event.type;
            trackedReq.eventParams = event.params;
            ProcessingQueue.getInstance().push(trackedReq);
        }
    }

    private static Logger logger = Logger.getLogger("wt1.tracker.api");
}