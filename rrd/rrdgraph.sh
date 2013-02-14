#! /bin/sh


SIZE=1000
RRD=wt1.rrd

rrdtool graph new_visits.png --start -$SIZE -h400 -w800 \
	--title="New visits" \
	"DEF:visitors=wt1.rrd:visitors:AVERAGE" \
	"DEF:visits=wt1.rrd:visits:AVERAGE" \
	"DEF:activeVisits=wt1.rrd:activeVisits:AVERAGE" \
	"DEF:revenue=wt1.rrd:revenue:AVERAGE" \
	"LINE2:visitors#ff0000:New visitors/s" \
	"GPRINT:visitors:AVERAGE:Average %6.0lf visitors/s" \
	"GPRINT:visitors:MIN:Min %6.0lf visitors/s" \
	"GPRINT:visitors:MAX:Max %6.0lf visitors/s\l" \
	"LINE2:visits#00ff00:New visits/s" \
	"GPRINT:visits:AVERAGE:Average %6.0lf visits/s" \
	"GPRINT:visits:MIN:Min %6.0lf visits/s" \
	"GPRINT:visits:MAX:Max %6.0lf visits/s" 

rrdtool graph active_visits.png --start -$SIZE -h400 -w800 \
	--title="Active visits" \
	"DEF:activeVisits=wt1.rrd:activeVisits:AVERAGE" \
	"LINE2:activeVisits#ff0000:Active visits" \
	"GPRINT:activeVisits:AVERAGE:Average %6.0lf" \
	"GPRINT:activeVisits:MIN:Min %6.0lf" \
	"GPRINT:activeVisits:MAX:Max %6.0lf" 


rrdtool graph revenue.png --start -$SIZE -h400 -w800 \
	--title="Revenue" \
	"DEF:revenue=wt1.rrd:revenue:AVERAGE" \
	"LINE2:revenue#ff0000:Revenue/s" \
	"GPRINT:revenue:AVERAGE:Average %6.0lf €/s" \
	"GPRINT:revenue:MIN:Min %6.0lf €/s" \
	"GPRINT:revenue:MAX:Max %6.0lf €/s" 
