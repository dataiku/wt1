package com.dataiku.wt1.storage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.Utils;

public class CSVFormatWriter {
    /** Compute the filename to use for a new output file */
    public static String newFileName(String instance) {
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy/MM/dd/HH/mm-ss");
        String name = "tracker/" + sdf1.format(new Date(now)) + (instance == null ? "" : "-" + instance) + ".log.gz";
        return name;
    }

    private static DateTimeFormatter isoFormatter = ISODateTimeFormat.dateHourMinuteSecondMillis().withZone(DateTimeZone.UTC);

    private static String escape(String in) {
        if (in == null) return "null";
        return in.replace("\"", "\\\"");
    }

    public CSVFormatWriter(Set<String> inlinedVP, Set<String> inlinedSP, Set<String> inlinedEP) {
        this.inlinedVP = inlinedVP;
        this.inlinedSP = inlinedSP;
        this.inlinedEP = inlinedEP;
    }

    private Set<String> inlinedVP;
    private Set<String> inlinedSP;
    private Set<String> inlinedEP;


    public String makeHeaderLine() {
        String s = "#server_ts\tclient_ts\tclient_addr\tvisitor_id\tlocation\treferer\tuser-agent\t"+
                "type\tvisitor_params\tsession_params\tevent_params\t" +
                "br_width\tbr_height\tsc_width\tsc_height\tbr_lang\ttz_off";
        for (String inlined : inlinedVP) {
            s += "\t" +inlined;
        }
        for (String inlined : inlinedSP) {
            s += "\t" +inlined;
        }
        for (String inlined : inlinedEP) {
            s += "\t" +inlined;
        }
        return s + "\n";
    }


    /**
     * Write the line of log for one request
     */
    public String makeLogLine(TrackedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(isoFormatter.print(req.serverTS));
        sb.append('\t');
        sb.append(isoFormatter.print(req.clientTS));
        sb.append('\t');
        sb.append(req.origAddress);
        sb.append('\t');
        sb.append(req.visitorId);
        sb.append("\t\"");
        sb.append(escape(req.page));
        sb.append("\"\t\"");
        sb.append(escape(req.referer));
        sb.append("\"\t\"");
        sb.append(escape(req.ua));
        sb.append("\"\t\"");
        sb.append(escape(req.type));
        sb.append("\"\t\"");
        sb.append(escape(req.visitorParams));
        sb.append("\"\t\"");
        sb.append(escape(req.sessionParams));
        sb.append("\"\t\"");
        sb.append(escape(req.eventParams));
        sb.append("\"\t");
        sb.append(req.browserWidth);
        sb.append("\t");
        sb.append(req.browserHeight);
        sb.append("\t");
        sb.append(req.screenWidth);
        sb.append("\t");
        sb.append(req.screenHeight);
        sb.append("\t\"");
        sb.append(req.browserLanguage);
        sb.append("\"\t");
        sb.append(req.tzOffset);

        if (req.visitorParams != null && inlinedVP.size() > 0) {
            doInline(Utils.decodeQueryString(req.visitorParams), inlinedVP, sb);
        }
        if (req.sessionParams != null && inlinedSP.size() > 0) {
            doInline(Utils.decodeQueryString(req.sessionParams), inlinedSP, sb);
        }
        if (req.eventParams != null && inlinedEP.size() > 0) {
            doInline(Utils.decodeQueryString(req.eventParams), inlinedEP, sb);
        }

        sb.append("\n");

        return sb.toString();
    }

    private static void doInline(Map<String, String[]> decoded, Set<String> inlineRules, StringBuilder sb) {
        for (String toInline : inlineRules) {
            String[] values = decoded.get(toInline);
            if (values != null && values.length > 0) {
                sb.append("\t\"");
                sb.append(escape(values[0]));
                sb.append("\"");
            } else {
                sb.append("\t\"\"");
            }
        }
    }
}
