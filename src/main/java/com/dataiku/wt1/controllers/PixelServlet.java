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

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.UUIDGenerator;
import com.dataiku.wt1.Utils;

@SuppressWarnings("serial")
public class PixelServlet extends HttpServlet {
    private byte[] pixelData;

    private static final String PIXEL_PATH = "/WEB-INF/spixel.gif";

    public static final String CLIENTTS_PARAM = "__wt1ts";
    public static final String TRACKTYPE_PARAM = "__wt1ty";
    public static final String REFERRER_PARAM = "__wt1ref";

    public static final String TZOFFSET_PARAM = "__wt1tzo";
    public static final String BROWSER_LANG_PARAM = "__wt1lang";
    public static final String BROWSER_WIDTH_PARAM = "__wt1bw";
    public static final String BROWSER_HEIGHT_PARAM = "__wt1bh";
    public static final String SCREEN_WIDTH_PARAM = "__wt1sw";
    public static final String SCREEN_HEIGHT_PARAM = "__wt1sh";

    public static final String VISITOR_ID_COOKIE = "__wt1vic";
    public static final String VISITOR_PARAMS_COOKIE = "__wt1vpc";
    public static final String SESSION_ID_COOKIE = "__wt1sic";
    public static final String SESSION_PARAMS_COOKIE = "__wt1spc";

    public static final String VISITOR_ID_THIRD_PARTY_COOKIE = "__wt1tpvic";
    public static final String VISITOR_ID_OPTOUT_COOKIE = "__wt1optout";
    
    public static final String DO_NOT_TRACK_HEADER = "DNT";

    public static final int VISITOR_ID_THIRD_PARTY_COOKIE_LIFETIME = 2 * 365 * 86400;
    public static final int VISITOR_ID_OPTOUT_COOKIE_LIFETIME = 5 * 365 * 86400;
    
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

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {

    	/* Get all current cookie values (visitor id, visitor params, session params) */
        String visitorIdCookieVal = req.getParameter(VISITOR_ID_COOKIE);
        String sessionIdCookieVal = req.getParameter(SESSION_ID_COOKIE);
        String visitorParamsCookieVal = req.getParameter(VISITOR_PARAMS_COOKIE);
        String sessionParamsCookieVal = req.getParameter(SESSION_PARAMS_COOKIE);

        /* If there is no global id and there is a visitor id with enough entropy, seed the global id from the visitor id
         * in order to avoid generating duplicates on race conditions generated from simultaneous asynchronous requests.
         */
        String seed = (visitorIdCookieVal != null && visitorIdCookieVal.length() >= 8) ? visitorIdCookieVal : null;
        String globalVisitorIdVal = getThirdPartyCookie(req, resp, true, seed);

        /* If we don't have visitor id or session id, generate some (prefixed with an identifier for 
         * their fakeness)
         * Note: it means that the user does not even accept first-party cookies
         */
        if (visitorIdCookieVal == null) {
            visitorIdCookieVal = "Z" + UUIDGenerator.generate();
        }
        if (sessionIdCookieVal == null) {
            sessionIdCookieVal = "Z" + UUIDGenerator.generate();
        }
        
        /* Make sure that the GIF does not get cached */
        // private = don't cache in proxy, only browser can cache
        // no-cache= always revalidate when in cache
        // no-cache=Set-Cookie : you can store the content in cache, but not this header
        // proxy-revalidate: Make sure some possibly-broken proxies don't interfere
        resp.addHeader("Cache-Control", "private, no-cache, no-cache=Set-Cookie, proxy-revalidate");
        
        resp.addHeader("Access-Control-Allow-Origin", "*");

        /* Send the GIF to the client */
        resp.setContentType("image/gif");
        resp.getOutputStream().write(pixelData);

        /* Enqueue the request for processing */
        TrackedRequest trackedReq = new TrackedRequest();
        trackedReq.origAddress = Utils.computeRealRemoteAddress(req);
        trackedReq.visitorId = visitorIdCookieVal;
        trackedReq.sessionId = sessionIdCookieVal;
        trackedReq.visitorParams = visitorParamsCookieVal;
        trackedReq.sessionParams = sessionParamsCookieVal;
        trackedReq.globalVisitorId = globalVisitorIdVal;
        trackedReq.page = req.getHeader("Referer");
        trackedReq.ua = req.getHeader("User-Agent");

        Map<String, String[]> reqParams = req.getParameterMap();
        trackedReq.fillEventParams(reqParams);

        ProcessingQueue.getInstance().push(trackedReq);
    }
    
    /**
     * Sets a tracking cookie, or remove it if lifetime is zero.
     */
    private static void setCookie(HttpServletResponse resp, String name, String value, int lifetime) {
		Cookie cookie = new Cookie(name, value);
		cookie.setMaxAge(lifetime);
		cookie.setPath("/");
		resp.addCookie(cookie);
    }

    /**
     * Sets or refreshes the third-party cookie, and returns its value.
     * @param generate Generate a global id if none
     * @param seed Seed to be used to generate the global id, or null if none
     * @return The global visitor id, or null if none, or the empty string if the user has opted out third-party tracking.  
     */
    public static String getThirdPartyCookie(HttpServletRequest req, HttpServletResponse resp,
    		boolean generate, String seed) {
    	if (! ProcessingQueue.getInstance().isThirdPartyCookies()) {
    		return null;
    	}

    	// Look for existing cookies
    	String globalVisitorId = null;
    	String optoutCookieVal = null;
    	if (req.getCookies() != null) {
    		for (Cookie cookie : req.getCookies()) {
    			if (cookie.getName().equals(VISITOR_ID_THIRD_PARTY_COOKIE)) {
    				globalVisitorId = cookie.getValue();
    			} else if (cookie.getName().equals(VISITOR_ID_OPTOUT_COOKIE)) {
    				optoutCookieVal = cookie.getValue();
    			}
    		}
    	}
    	
    	// Send P3P header if configured
    	String p3pHeader = ProcessingQueue.getInstance().getP3PHeader();
    	if (p3pHeader != null) {
    		resp.setHeader("P3P", p3pHeader);
    	}
    	
    	// Parse and echo DNT header if present
    	if (! ProcessingQueue.getInstance().isIgnoreDNT()) {
    		String doNotTrackVal = req.getHeader(DO_NOT_TRACK_HEADER);
    		if (doNotTrackVal != null) {
    			resp.addHeader(DO_NOT_TRACK_HEADER, doNotTrackVal);
    			if (doNotTrackVal.equals("1")) {
    				// Remove any third-party cookie
    				if (globalVisitorId != null) {
    					setCookie(resp, VISITOR_ID_THIRD_PARTY_COOKIE, "0", 0);
    				}
    				if (optoutCookieVal != null) {
    					setCookie(resp, VISITOR_ID_OPTOUT_COOKIE, "0", 0);
    				}
    				return "";
    			}
    		}
    	}
    	
    	if (optoutCookieVal != null && optoutCookieVal.equals("1")) {
    		/* Refresh the opt-out cookie. */
    		setCookie(resp, VISITOR_ID_OPTOUT_COOKIE, "1", VISITOR_ID_OPTOUT_COOKIE_LIFETIME);
    		return "";
    	}

    	if (globalVisitorId == null || globalVisitorId.equals("0")) {
    		if (generate) {
    			globalVisitorId = (seed == null) ? UUIDGenerator.generate() : UUIDGenerator.fromSeed(seed);
    		} else {
    			globalVisitorId = null;
    		}
    	}
    	if (globalVisitorId != null) {
    		/* Refresh the global visitor id cookie */
    		setCookie(resp, VISITOR_ID_THIRD_PARTY_COOKIE, globalVisitorId, VISITOR_ID_THIRD_PARTY_COOKIE_LIFETIME);
    	}
    	return globalVisitorId;
    }
    
    /**
     * Sets / resets the third-party opt-out cookie.
     */
    public static void setOptout(HttpServletRequest req, HttpServletResponse resp, boolean optout) {
    	if (! ProcessingQueue.getInstance().isThirdPartyCookies()) {
    		return;
    	}

    	/* Look for existing cookies. */
    	String globalVisitorId = null;
    	String optoutCookieVal = null; 
    	if (req.getCookies() != null) {
    		for (Cookie cookie : req.getCookies()) {
    			if (cookie.getName().equals(VISITOR_ID_THIRD_PARTY_COOKIE)) {
    				globalVisitorId = cookie.getValue();
    			} else if (cookie.getName().equals(VISITOR_ID_OPTOUT_COOKIE)) {
    				optoutCookieVal = cookie.getValue();
    			}
    		}
    	}

    	// Send P3P header if configured
    	String p3pHeader = ProcessingQueue.getInstance().getP3PHeader();
    	if (p3pHeader != null) {
    		resp.setHeader("P3P", p3pHeader);
    	}

    	// Parse and echo DNT header if present
    	if (! ProcessingQueue.getInstance().isIgnoreDNT()) {
    		String doNotTrackVal = req.getHeader(DO_NOT_TRACK_HEADER);
    		if (doNotTrackVal != null) {
    			resp.addHeader(DO_NOT_TRACK_HEADER, doNotTrackVal);
    			if (doNotTrackVal.equals("1")) {
    				// Remove any third-party cookie
    				if (globalVisitorId != null) {
    					setCookie(resp, VISITOR_ID_THIRD_PARTY_COOKIE, "0", 0);
    				}
    				if (optoutCookieVal != null) {
    					setCookie(resp, VISITOR_ID_OPTOUT_COOKIE, "0", 0);
    				}
    				return;
    			}
    		}
    	}
    	
    	if (optout) {
    		/* Expire the global id cookie if any. */
    		if (globalVisitorId != null) {
    			setCookie(resp, VISITOR_ID_THIRD_PARTY_COOKIE, "0", 0);
    		}
    		/* Set / refresh the opt-out cookie. */
    		setCookie(resp, VISITOR_ID_OPTOUT_COOKIE, "1", VISITOR_ID_OPTOUT_COOKIE_LIFETIME);

    	} else {
    		/* Expire the opt-out cookie if any. */
    		if (optoutCookieVal != null) {
    			setCookie(resp, VISITOR_ID_OPTOUT_COOKIE, "0", 0);
    		}
    	}
    }
    
    private static Logger logger = Logger.getLogger("wt1.tracker");
}