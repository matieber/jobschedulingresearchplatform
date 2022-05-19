import requests
import json

#payload = {'handle': '1'}
#data=json.dumps(payload)
#header={'Content-Type': 'application/json', "encoding":"UTF-8"}
#use http://localhost:1080/connection/all to connect or disconnect all registered devices
#r = requests.put("http://192.168.0.116/OnOff", data=json.dumps(payload))
try:
    r = requests.put("http://192.168.0.116/OnOff?hanlde=9")
    print(str(r.text))
except Exception as e:
    raise e
#payload = {'requiredEnergyState': 'discharging', 'slotId':'1'}
#r = requests.put("http://192.168.0.116/energy/motorola_moto_g6", data=json.dumps(payload))

#get devices info example:
#param = {'handle': '9'}
#r = requests.get("http://192.168.0.124/OnOff", headers=header, params=param)

