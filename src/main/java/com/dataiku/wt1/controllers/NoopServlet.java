package com.dataiku.wt1.controllers;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

@SuppressWarnings("serial")
/* A "no-op" servlet that only serves the static pixel data, used for benching purposes */
public class NoopServlet extends HttpServlet {
	private byte[] pixelData;
	private static final String PIXEL_PATH = "/WEB-INF/spixel.gif";
	
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
		resp.addHeader("Cache-Control", "private, no-cache, no-cache=Set-Cookie, proxy-revalidate");
		resp.setContentType("image/gif");
		resp.getOutputStream().write(pixelData);
	}
	private static Logger logger = Logger.getLogger("wt1.tracker");
}