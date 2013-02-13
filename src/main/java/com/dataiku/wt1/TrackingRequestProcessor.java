package com.dataiku.wt1;

import java.io.IOException;
import java.util.Map;

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
     * Called when the system is shutting down. The processor must flush all
     * pending events and IOs.
     */
    public void shutdown() throws IOException;
}