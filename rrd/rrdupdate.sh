#! /bin/sh

DATA=`GET http://localhost:8080/wt1/handlers/1`

VISITORS=`echo $DATA|cut -d ':' -f 2|cut -d ',' -f 1`
VISITS=`echo $DATA|cut -d ':' -f 3|cut -d ',' -f 1`
AVISITS=`echo $DATA|cut -d ':' -f 4|cut -d ',' -f 1`

REVENUE=`echo $DATA|sed 's,.*totalValue,,g'|cut -d ':' -f 2 |cut -d '}' -f 1|cut -d '.' -f 1`

set -x

rrdtool update wt1.rrd N:$VISITORS:$VISITS:$AVISITS:$REVENUE
