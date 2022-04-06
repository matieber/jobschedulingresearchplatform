import requests
import json

payload = {'requiredVirtualConnectionState':'connected'}
data=json.dumps(payload)
#header={'Content-Type': 'application/json', "encoding":"UTF-8"}
#use http://localhost:1080/connection/all to connect or disconnect all registered devices
r = requests.put("http://localhost:1080/connection/samsung_SM_A305G",  data=json.dumps(payload))
print(r.text)

#get devices info example:

#param = {'connected': "any"}
#r = requests.get("http://localhost:1080/info/all", headers=header, params=param)
#print(r.text)