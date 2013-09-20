package com.dataiku.wt1.gae;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dataiku.wt1.ProcessingQueue;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import com.google.appengine.api.LifecycleManager;
import com.google.appengine.api.LifecycleManager.ShutdownHook;
import com.google.appengine.api.ThreadManager;

/**
 * Servlet mounted on the GAE /_ah/start handler, called at backend initialization.
 * Its job is to start and stop the processing queue, and to configure logging.
 */
public class InitializationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_PATH = "/WEB-INF/config.properties";	

    public void registerShutdownHook() {
        LifecycleManager.getInstance().setShutdownHook(new ShutdownHook() {
            public void shutdown() {
                logger.info("App will stop");
                ProcessingQueue.getInstance().setShutdown();
            }
        });
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        System.out.println("*********************");
        Logger.getRootLogger().removeAllAppenders();
        BasicConfigurator.configure();

        registerShutdownHook();

        /* Load config */
        try {
            File configFile = new File(getServletContext().getRealPath(CONFIG_PATH));
            Properties props = new Properties();
            props.load(new FileReader(configFile));
            props.load(new FileReader(configFile));

            ProcessingQueue.getInstance().configure(props);

            /* Start all processing threads */
            for (Runnable runnable : ProcessingQueue.getInstance().getRunnables()) {
                Thread thread = ThreadManager.createBackgroundThread(runnable);
                thread.start();
            }

        } catch (Exception e) {
            throw new IOException("Could not create processing threads", e);
        }
        logger.info("App started");
    }

    Logger logger = Logger.getLogger("wt1");
}