package com.dataiku.wt1.controllers;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.dataiku.wt1.ConfigConstants;
import com.dataiku.wt1.ProcessingQueue;

public class IsAliveServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	
	private File maintenanceFile;

    @Override
    public void init(ServletConfig config) throws ServletException {
        String filename = ProcessingQueue.getInstance().getConfiguration().getProperty(ConfigConstants.MAINTENANCE_FILE);
        if (filename != null) {
        	maintenanceFile = new File(filename);
        	logger.info("Watching maintenance file " + filename);
        }
    }
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	if (maintenanceFile != null && maintenanceFile.exists()) {
    		resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is under maintenance");
    	} else {
    		resp.setContentType("text/plain");
    		resp.getWriter().write("Server is alive\r\n");
    	}
    }
    
    private static Logger logger = Logger.getLogger("wt1.tracker");
}
