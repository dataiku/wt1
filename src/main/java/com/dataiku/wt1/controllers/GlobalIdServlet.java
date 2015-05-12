package com.dataiku.wt1.controllers;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class GlobalIdServlet extends HttpServlet {

	public static final String FUN_PARAM = "fun";

	/*
	 * Called with ?fun=FUN
	 * Returns Javascript script calling FUN("my_global_visitor_id")
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
	    String globalVisitorIdVal = PixelServlet.getThirdPartyCookie(req, resp, true, null);
        if (globalVisitorIdVal == null) {
            globalVisitorIdVal = "";
        }
	    
		String fun = req.getParameter(FUN_PARAM);
		if (fun == null) {
			resp.setContentType("application/json");
            resp.getWriter().write("{\"id\": \""+ globalVisitorIdVal + "\" }");
		} else {
		    resp.setContentType("application/x-javascript");
	        resp.getWriter().write(fun + "(\"" + globalVisitorIdVal + "\");");
		}
	}
}
