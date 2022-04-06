import requests

params = "?requiredEnergyState=discharging&slotId=1"
device = "motorola_moto_g9_play"
r = requests.put("http://localhost:1080/energy/" + device + params)
print(r.text)