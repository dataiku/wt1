package com.dataiku.wt1.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.Utils;

import org.apache.log4j.Logger;

import com.google.gson.Gson;

/**
 * Implements counts and sums in real time.
 * 
 * Standard counters:
 *   - the number of visitors since startup
 *   - the number of unique visits since startup
 *  
 * Standard gauges:
 *   - the number of "currently active" visits
 * 
 * Additionally, it exposes counters for an arbitrary number of sums:
 * A sum is made of:
 *   - a filter key (on event params)
 *   - a filter value (on event params). 
 *      For example, "key=type, value=purchase"
 *   - a value field on event params (for example "amount")
 *   
 * Tracking visitors can use large amounts of memory, so it can be disabled using "trackVisitors=false"
 * (Approximatively 1 GB for 6M visitors)
 */
public class CounterHandler implements TrackingRequestProcessor {
    private Set<String> seenVisitors = new HashSet<String>();
    private long previousSeenVisits;
    private Map<String, Long> activeVisits = new HashMap<String, Long>(); // Value = last active timestamp

    private boolean trackVisitors = true;

    static class Sum {
        transient String sumName;
        transient String filterKey;
        transient String filterValue;
        transient String valueKey;
        int nbElems;
        // XX FIXME
        long totalValue;
    }
    private Map<String, Sum> sums = new HashMap<String, CounterHandler.Sum>();

    class SessionsExpirationThread extends Thread {
        volatile boolean shutdown;
        public void run() {
            while (!shutdown) {
                logger.debug("Running eviction");
                synchronized(CounterHandler.this) {
                    long minTime = System.currentTimeMillis() - 1000*ProcessingQueue.getInstance().getSessionExpirationTimeS();
                    List<String> toMove = new ArrayList<String>();
                    for (Map.Entry<String, Long> activeVisit : activeVisits.entrySet()) {
                        if (activeVisit.getValue() < minTime) {
                            toMove.add(activeVisit.getKey());
                        }
                    }
                    logger.debug("Evicting " + toMove.size() + " sessions");
                    for (String entry : toMove) {
                        previousSeenVisits++;
                        activeVisits.remove(entry);
                    }
                    logger.debug("Evicted " + toMove.size() + " sessions");

                }
                synchronized (this) {
                    try { this.wait(10000); } catch (InterruptedException e) {}
                }
            }
        }
    }

    @Override
    public void init(Map<String, String> params) throws IOException {
        for (String key : params.keySet()) {
            if (key.equals("trackVisitors") && params.get(key).equalsIgnoreCase("false")) trackVisitors = false;

            if (key.startsWith("sum.")) {
                String[] chunks = key.split("\\.");
                String sumName = chunks[1];
                Sum sum = sums.get(sumName);
                if (sum == null) {
                    sum = new Sum();
                    sum.sumName = sumName;
                    sums.put(sumName, sum);
                }
                String paramName = chunks[2];
                if (paramName.equals("filterKey")) sum.filterKey = params.get(key);
                else if (paramName.equals("filterValue")) sum.filterValue = params.get(key);
                else if (paramName.equals("valueKey")) sum.valueKey = params.get(key);
                else {
                    throw new IllegalArgumentException("Unknown param " + key + " " + paramName);
                }
            }
        }
        set.start();
    }
    SessionsExpirationThread set = new SessionsExpirationThread();


    static class Message {
        long visitorsCounter;
        long visitsCounter;
        long activeVisitsGauge;
        Map<String, Sum> sums;
    }

    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String json  = null;
        synchronized (this) {
            Message m = new Message();
            m.visitorsCounter = seenVisitors.size();
            m.visitsCounter = previousSeenVisits + activeVisits.size();
            m.activeVisitsGauge = activeVisits.size();
            m.sums = sums;
            json = new Gson().toJson(m);
        }
        resp.setContentType("application/json");
        resp.getWriter().write(json);
    }

    @Override
    public void process(TrackedRequest req) throws IOException {
        synchronized (this) {
            if (trackVisitors) {
                if (!seenVisitors.contains(req.visitorId)) {
                    seenVisitors.add(req.visitorId);
                }
            }
            activeVisits.put(req.sessionId, req.serverTS);
            if (sums.size() > 0) {
                Map<String, String[]> eparams =  Utils.decodeQueryString(req.eventParams);

                for (Sum sum : sums.values()) {
                    String[] filterV = eparams.get(sum.filterKey);
                    if (filterV != null && filterV.length > 0 && filterV[0].equals(sum.filterValue)) {
                        String[] value = eparams.get(sum.valueKey);
                        if (value.length > 0) {
                            try {
                                sum.totalValue += Double.parseDouble(value[0]);
                                sum.nbElems++;
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void shutdown() throws IOException {
        synchronized (set) {
            set.shutdown = true;
            set.notifyAll();
        }
        try {
            set.join();
        } catch (InterruptedException e) {
        }
    }

    private static final Logger logger = Logger.getLogger("wt1.handler.count");
}