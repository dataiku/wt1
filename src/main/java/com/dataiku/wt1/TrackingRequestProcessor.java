package com.dataiku.wt1;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base processor for tracked requests.
 * All methods of this processor are called in a single thread.
 */
public interface TrackingRequestProcessor {
    /**
     * Called to initialize the processor. 
     */
    public void init(Map<String, String> processorParams) throws IOException;

    /**
     * Process one request for
     * @param req
     * @throws IOException
     */
    public void process(TrackedRequest req) throws IOException;

    /**
     * Process an HTTP request for this handler
     */
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException;
    
    /**
     * Called when the system is shutting down. The processor must flush all
     * pending events and IOs.
     */
    public void shutdown() throws IOException;
}