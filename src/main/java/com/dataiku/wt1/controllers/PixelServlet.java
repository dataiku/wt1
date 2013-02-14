package com.dataiku.wt1.controllers;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.UUIDGenerator;
import com.dataiku.wt1.Utils;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class PixelServlet extends HttpServlet {
    private byte[] pixelData;

    private static final String PIXEL_PATH = "/WEB-INF/spixel.gif";

    public static final String CACHEKILL_PARAM = "__wt1ts";
    public static final String TRACKTYPE_PARAM = "__wt1ty";
    public static final String REFERRER_PARAM = "__wt1ref";

    public static final String TZOFFSET_PARAM = "__wt1tzo";
    public static final String BROWSER_LANG_PARAM = "__wt1lang";
    public static final String BROWSER_WIDTH_PARAM = "__wt1bw";
    public static final String BROWSER_HEIGHT_PARAM = "__wt1bh";
    public static final String SCREEN_WIDTH_PARAM = "__wt1sw";
    public static final String SCREEN_HEIGHT_PARAM = "__wt1sh";

    private static final String VISITOR_ID_COOKIE = "__wt1vic";
    private static final String VISITOR_PARAMS_COOKIE = "__wt1vpc";
    private static final String SESSION_ID_COOKIE = "__wt1sic";
    private static final String SESSION_PARAMS_COOKIE = "__wt1spc";

    public static final String VISITOR_PARAMS_SET_ARG = "__wt1vpsa";
    public static final String VISITOR_PARAMS_DEL_ARG = "__wt1vpda";
    public static final String VISITOR_PARAMS_CLEAR_ARG = "__wt1vpca";

    public static final String SESSION_PARAMS_SET_ARG = "__wt1spsa";
    public static final String SESSION_PARAMS_DEL_ARG = "__wt1spda";
    public static final String SESSION_PARAMS_CLEAR_ARG = "__wt1spca";

    @Override
    public void init(ServletConfig config) throws ServletException {
        ServletContext ctx = config.getServletContext();

        try {
            File f = new File(ctx.getRealPath(PIXEL_PATH));
            pixelData = FileUtils.readFileToByteArray(f);
        } catch (IOException e) {
            logger.error("Failed to read pixel", e);
            throw new ServletException(e);
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long before = System.currentTimeMillis();

        TrackedRequest trackedReq = new TrackedRequest();

        /* Find all current cookies (visitor id, visitor params, session params) */
        String visitorIdCookieVal = null;
        String sessionIdCookieVal = null;
        String visitorParamsCookieVal = null;
        String sessionParamsCookieVal = null;
        if (req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if (cookie.getName().equals(VISITOR_ID_COOKIE)) {
                    visitorIdCookieVal = cookie.getValue();
                } else if (cookie.getName().equals(SESSION_ID_COOKIE)) {
                    sessionIdCookieVal = cookie.getValue();
                } else if (cookie.getName().equals(VISITOR_PARAMS_COOKIE)) {
                    visitorParamsCookieVal = cookie.getValue();
                } else if (cookie.getName().equals(SESSION_PARAMS_COOKIE)) {
                    sessionParamsCookieVal = cookie.getValue();
                }
            }
        }

        /* Set the visitor id cookie if needed */
        if (visitorIdCookieVal == null) {
            visitorIdCookieVal = UUIDGenerator.generate();
            Cookie visitorIdCookie = new Cookie(VISITOR_ID_COOKIE, visitorIdCookieVal);
            visitorIdCookie.setMaxAge(365 * 86400);
            visitorIdCookie.setPath("/");
            resp.addCookie(visitorIdCookie);
        }
        /* Set the session id cookie. Always send it to refresh its expiration date */
        if (sessionIdCookieVal == null) {
            sessionIdCookieVal = UUIDGenerator.generate();
        }
        Cookie sessionIdCookie = new Cookie(SESSION_ID_COOKIE, sessionIdCookieVal);
        sessionIdCookie.setMaxAge(ProcessingQueue.getInstance().getSessionExpirationTimeS());
        sessionIdCookie.setPath("/");
        resp.addCookie(sessionIdCookie);

        /* Update the visitor params cookie, if some arguments to modify them were passed in */
        {
            String set = req.getParameter(VISITOR_PARAMS_SET_ARG);
            String del = req.getParameter(VISITOR_PARAMS_DEL_ARG);
            String clear = req.getParameter(VISITOR_PARAMS_CLEAR_ARG);
            /* The cookie needs to be updated */
            if (set != null || del != null || clear != null) {
                visitorParamsCookieVal = updateCookieVal(visitorParamsCookieVal, set, del, clear);
                Cookie visitorParamsCookie = new Cookie(VISITOR_PARAMS_COOKIE, visitorParamsCookieVal);
                visitorParamsCookie.setMaxAge(365 * 86400);
                visitorParamsCookie.setPath("/");
                resp.addCookie(visitorParamsCookie);
            }
        }
        /* Update the session params cookie, if some arguments to modify them were passed in */
        {
            String set = req.getParameter(SESSION_PARAMS_SET_ARG);
            String del = req.getParameter(SESSION_PARAMS_DEL_ARG);
            String clear = req.getParameter(SESSION_PARAMS_CLEAR_ARG);
            /* The cookie needs to be updated */
            if (set != null || del != null || clear != null) {
                sessionParamsCookieVal = updateCookieVal(sessionParamsCookieVal, set, del, clear);
            }
            /* The session cookie is always set, to refresh its expiration date */
            Cookie sessionParamsCookie = new Cookie(SESSION_PARAMS_COOKIE, sessionParamsCookieVal);
            sessionParamsCookie.setPath("/");
            sessionParamsCookie.setMaxAge(ProcessingQueue.getInstance().getSessionExpirationTimeS());
            resp.addCookie(sessionParamsCookie);
        }

        /* Make sure that the GIF does not get cached */
        // private = don't cache in proxy, only browser can cache
        // no-cache= always revalidate when in cache
        // no-cache=Set-Cookie : you can store the content in cache, but not this header
        // revalidate: Make sure some possibly-broken proxies don't interfere
        resp.addHeader("Cache-Control", "private, no-cache, no-cache=Set-Cookie, proxy-revalidate");

        /* Send the GIF to the client */
        resp.setContentType("image/gif");
        resp.getOutputStream().write(pixelData);

        /* Enqueue the request for processing */
        trackedReq.origAddress = req.getRemoteAddr();
        trackedReq.visitorId = visitorIdCookieVal;
        trackedReq.sessionId = sessionIdCookieVal;
        trackedReq.visitorParams = visitorParamsCookieVal;
        trackedReq.sessionParams = sessionParamsCookieVal;
        trackedReq.page = req.getHeader("Referer");
        trackedReq.ua = req.getHeader("User-Agent");

        @SuppressWarnings("unchecked")
        Map<String, String[]> reqParams = req.getParameterMap();
        trackedReq.fillEventParams(reqParams);

        ProcessingQueue.getInstance().push(trackedReq);

        //		logger.info("qtime=" + (System.currentTimeMillis()-before));
    }

    /**
     * Computes the updated value for a params cookie, given the old value of the cookie, and the
     * values of the HTTP params to set/delete/clear some elements in the parameters
     */
    private String updateCookieVal(String oldCookieVal, String set, String del, String clear) {
        Map<String, String[]> params = Utils.decodeQueryString(oldCookieVal);
        if (clear != null) params.clear();
        if (set != null) {
            Map<String, String[]> paramsToSet = Utils.decodeQueryString(set);
            for (Map.Entry<String, String[]> e : paramsToSet.entrySet()) {
                params.put(e.getKey(), e.getValue());
            }
        }
        if (del != null) {
            Map<String, String[]> paramsToDelete = Utils.decodeQueryString(del);
            for (String k : paramsToDelete.keySet()) {
                params.remove(k);
            }
        }
        return Utils.encodeQueryString(params);
    }

    private static Logger logger = Logger.getLogger("wt1.tracker");
}