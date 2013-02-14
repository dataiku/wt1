package com.dataiku.wt1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

/**
 * Asynchronous request processing multi-queue (one queue per handler  + one for the storage)
 */
public class ProcessingQueue {
    public static class QueueHandlerStats {
        public QueueHandlerStats(String name) {
            this.name = name;
        }
        public String name;
        public long queuedEvents;
        public long lostInQueueEvents;
        public long processedEvents;
        public long processFailedEvents;
    }

    public static class Stats {
        public long receivedEvents;
        public QueueHandlerStats storage;
        public List<QueueHandlerStats> handlers = new ArrayList<ProcessingQueue.QueueHandlerStats>();
        /* Storage specific */
        public long savedEvents;
        public long createdFiles;
        public long savedEventsInputSize;
        public long savedEventsGZippedSize;
    }

    public class QueueHandler implements Runnable {
        public String getName() {
            return localStats.name;
        }
        public TrackingRequestProcessor getProcessor() {
            return processor;
        }
        QueueHandlerStats localStats;
        TrackingRequestProcessor processor;
        LinkedBlockingQueue<TrackedRequest> inQueue;

        public void run() {
            Thread.currentThread().setName("Queue-" + localStats.name);
            logger.info("Processing queue starting up for " + localStats.name);
            while (!shutdown) {
                TrackedRequest req = null;
                try {
                    req = inQueue.poll(2000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    continue;
                }
                if (req == null) {
                    continue;
                }
                synchronized(stats) {
                    localStats.processedEvents++;
                }
                try {
                    processor.process(req);
                } catch (Exception e){
                    synchronized(stats) {
                        localStats.processFailedEvents++;
                    }
                    logger.info("Processing error", e);
                }
            }
            logger.info("Tracker thread for " + localStats.name + " shutting down");
            try {
                processor.shutdown();
            } catch (Exception e) {
                logger.error("Failed to close last file", e);
            }
        }
    }

    // Last events received on input
    private LimitedQueue<TrackedRequest> lastQueue = new LimitedQueue<TrackedRequest>(100);
    private Stats stats = new Stats();
    private volatile boolean shutdown;
    
    // Configuration
    private int queueSize;
    private int  sessionExpirationTimeS;

    private List<QueueHandler> handlers = new ArrayList<ProcessingQueue.QueueHandler>();

    public List<QueueHandler> getQueueHandlers() {
        return handlers;
    }

    public List<? extends Runnable> getRunnables() {
        return handlers;
    }
    
    public int getSessionExpirationTimeS() {
        return sessionExpirationTimeS;
    }
    
    public void configure(Properties props) throws Exception {
        /* Global config */
        queueSize = Integer.parseInt(props.getProperty(ConfigConstants.MAX_QUEUE_SIZE_PARAM, ConfigConstants.DEFAULT_MAX_QUEUE_SIZE));
        sessionExpirationTimeS =  Integer.parseInt(props.getProperty(ConfigConstants.SESSION_EXPIRATION_PARAM, ConfigConstants.DEFAULT_SESSION_EXPIRATION));

        /* Load main storage processor from config */
        {
            String processorClass = props.getProperty("storage.class");
            Map<String, String> processorParams = new HashMap<String, String>();
            for (Object _k : props.keySet()) {
                String k = (String)_k;
                if (k.startsWith("storage.params.")) {
                    processorParams.put(k.replace("storage.params.", ""), props.getProperty(k));
                }
            }

            TrackingRequestProcessor processor = (TrackingRequestProcessor) Class.forName(processorClass).newInstance();
            processor.init(processorParams);
            QueueHandler storageHandler = new QueueHandler();
            storageHandler.localStats = new QueueHandlerStats("storage");
            storageHandler.inQueue = new LinkedBlockingQueue<TrackedRequest>(queueSize);
            storageHandler.processor = processor;
            stats.storage = storageHandler.localStats;
            handlers.add(storageHandler);
        }

        /* Load handlers */
        for (Object _k : props.keySet()) {
            String k = (String)_k;
            if (k.startsWith("handler.") && k.endsWith(".class")) {
                String name = k.replace("handler.", "").replace(".class", "");
                TrackingRequestProcessor handler = configureHandler(props, name);
                QueueHandler queueHandler = new QueueHandler();
                queueHandler.localStats = new QueueHandlerStats(name);
                queueHandler.inQueue = new LinkedBlockingQueue<TrackedRequest>(queueSize);
                queueHandler.processor = handler;
                stats.handlers.add(queueHandler.localStats);
                handlers.add(queueHandler);
            }
        }
    }

    private TrackingRequestProcessor configureHandler(Properties props, String name) throws Exception {
        String handlerclass = props.getProperty("handler." + name + ".class");
        Map<String, String> handlerParams = new HashMap<String, String>();
        for (Object _k : props.keySet()) {
            String k = (String)_k;
            if (k.startsWith("handler." + name + ".params.")) {
                handlerParams.put(k.replace("handler." + name + ".params.", ""), props.getProperty(k));
            }
        }

        TrackingRequestProcessor processor = (TrackingRequestProcessor) Class.forName(handlerclass).newInstance();
        processor.init(handlerParams);
        return processor;
    }

    /** You must synchronize on the returned object */
    public Stats getStats() {
        return stats;
    }

    /** Notify the queue that the application is shutting down */
    public void setShutdown() {
        this.shutdown = true;
    }

    public void fillLastReceived(List<TrackedRequest> target) {
        synchronized (lastQueue) {
            target.addAll(lastQueue);
        }
    }

    /** 
     * Send a tracked request to the queue for asynchronous processing.
     * The request might be lost partially or totally here
     */
    public void push(TrackedRequest req) {
        synchronized (lastQueue) {
            lastQueue.add(req);
        }
        synchronized(stats) {
            stats.receivedEvents++;
        }
        /* Enqueue on all handlers */
        for (QueueHandler handler : handlers) {
            try {
                boolean success = handler.inQueue.offer(req, 5, TimeUnit.MILLISECONDS);
                synchronized (stats) { 
                    if (success) {
                        handler.localStats.queuedEvents++;
                    } else {
                        handler.localStats.lostInQueueEvents++;
                    }
                }
            } catch (Exception e)  {
                synchronized (stats) { 
                    handler.localStats.lostInQueueEvents++;
                }
            }
        }
    }

    private static ProcessingQueue instance = new ProcessingQueue();
    public static ProcessingQueue getInstance() {
        return instance;
    }

    private static final Logger logger = Logger.getLogger("wt1.queue");
}