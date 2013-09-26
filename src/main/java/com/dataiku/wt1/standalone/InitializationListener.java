package com.dataiku.wt1.standalone;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.dataiku.wt1.ProcessingQueue;
import org.apache.log4j.Logger;

/**
 * Backend initialization
 * Its job is to start and stop the processing queue, and to configure logging.
 */
public class InitializationListener implements ServletContextListener {
    private static final String CONFIG_PATH = "/WEB-INF/config.properties";

    private List<Thread> threads = new ArrayList<Thread>();

    Logger logger = Logger.getLogger("wt1");


    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        logger.info("Application stopping");
        ProcessingQueue.getInstance().setShutdown();
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {}
        }
        logger.info("Application stopped");
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            /* Load config */
            File configFile = new File(event.getServletContext().getRealPath(CONFIG_PATH));
            Properties props = new Properties();
            props.load(new FileReader(configFile));
            ProcessingQueue.getInstance().configure(props);

            /* Start all processing threads */
            for (Runnable runnable : ProcessingQueue.getInstance().getRunnables()) {
                Thread thread = new Thread(runnable);
                threads.add(thread);
                thread.start();
            }
        } catch (Exception e) {
            throw new Error("Could not create processing queue", e);
        }
        logger.info("Application started");
    }
}