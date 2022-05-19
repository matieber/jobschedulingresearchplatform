import requests
import json

#header={'Content-Type': 'application/json', "encoding":"UTF-8"}
#use http://localhost:1080/connection/all to connect or disconnect all registered devices
r = requests.put("http://localhost:1080/job/Xiaomi_Redmi_Note_7")
print(r.text)

#get devices info example:

#param = {'connected': "any"}
#r = requests.get("http://localhost:1080/info/all", headers=header, params=param)
#print(r.text)
