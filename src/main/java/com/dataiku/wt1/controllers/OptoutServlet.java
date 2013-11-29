package com.dataiku.wt1.controllers;

import java.io.IOException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.dataiku.wt1.ConfigConstants;
import com.dataiku.wt1.ProcessingQueue;

@SuppressWarnings("serial")
public class OptoutServlet extends HttpServlet {

	// Opt-out callback URL read from configuration
	// if null, opt-out status will return simple text/plain results
	private String optoutCallbackUrl;
	
    @Override
    public void init(ServletConfig config) throws ServletException {
    	optoutCallbackUrl = ProcessingQueue.getInstance().getConfiguration().getProperty(ConfigConstants.OPTOUT_CALLBACK_URL);
    	if (optoutCallbackUrl == null) {
    		logger.warn("No opt-out callback URL, callback disabled");
    	}
    }

    /**
	 * Optout management servlet
	 * To be called as follows:
	 * 	/status	=> returns redirect to OPTOUT_CALLBACK_URL?status=(cookie|nocookie|optedout)
	 *  /optin => opts in and redirects to /status
	 *  /optout => opts out and redirects to /status
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	if (! ProcessingQueue.getInstance().isThirdPartyCookies()) {
            resp.setStatus(400);
    		return;
    	}

		String pathInfo = req.getPathInfo();
		if (pathInfo == null) {
            resp.setStatus(400);
            return;
        }
		
		if (pathInfo.equals("/status")) {
			String globalVisitorId = PixelServlet.getThirdPartyCookie(req, resp, false, null);
			if (globalVisitorId == null) {
				if (optoutCallbackUrl != null) {
					resp.sendRedirect(optoutCallbackUrl + "?status=nocookie");
				} else {
					resp.setContentType("text/plain");
					resp.getWriter().write("nocookie\r\n");
				}
			} else if (globalVisitorId.equals("")) {
				if (optoutCallbackUrl != null) {

					resp.sendRedirect(optoutCallbackUrl + "?status=optedout");
				} else {
					resp.setContentType("text/plain");
					resp.getWriter().write("optedout\r\n");
				}
			} else {
				if (optoutCallbackUrl != null) {
					resp.sendRedirect(optoutCallbackUrl + "?status=cookie");
				} else {
					resp.setContentType("text/plain");
					resp.getWriter().write("cookie\r\n");
				}
			}

        } else if (pathInfo.equals("/optout")) {
        	PixelServlet.setOptout(req, resp, true);
    		resp.sendRedirect("status");
        	
        } else if (pathInfo.equals("/optin")) {
        	PixelServlet.setOptout(req, resp, false);
    		resp.sendRedirect("status");
        	
        } else {
            resp.setStatus(400);
        }
	}

	Logger logger = Logger.getLogger("wt1.optout");
}
