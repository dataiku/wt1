#! /bin/sh

# Keeping in RRA
#  - 6 hours at 1 minute   - aggregation of 1 point     - 360 values
#  - 1 day at 2 minutes    - aggregation of 2 points    - 720 values
#  - 7 day at 16 minutes   - aggregation of 16 points   - 630 values
#  - 30 days at 1h         - aggregation of 120 points  - 720 values
#  - 1 year at 12h         - aggregation of 1440 points - 730 values
STEP=20
HB=180
RRA="RRA:AVERAGE:0.5:1:360 RRA:AVERAGE:0.5:2:720 RRA:AVERAGE:0.5:16:630 RRA:AVERAGE:0.5:120:720 RRA:AVERAGE:0.5:1440:730"

rrdtool create wt1.rrd --step $STEP \
	"DS:visitors:COUNTER:$HB:0:U" \
	"DS:visits:COUNTER:$HB:0:U" \
	"DS:activeVisits:GAUGE:$HB:0:U" \
	"DS:revenue:COUNTER:$HB:0:U" \
	$RRA
