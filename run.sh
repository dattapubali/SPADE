#!/usr/bin/bash

./bin/spade stop
/bin/rm ./log/*
ps -fA | grep -E 'SPADE'| awk '{print $2}'| while read line; do sudo kill -9 "$line"; done
sudo /bin/rm -f /var/log/audit/audit.log
sudo service auditd restart
/bin/rm -f /tmp/provenance.dot
/bin/rm -f /tmp/audit.log
/bin/rm -rf /tmp/spade.graph_db
./bin/spade start
sleep 5
auditctl -l
#sudo sh $1
