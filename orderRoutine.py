import time
import json
import requests
import sys
from requests.auth import HTTPBasicAuth

headers = {'content-type': 'application/json'}
auth = HTTPBasicAuth('m.rossini@yopmail.com', 'password')
body = [
        {
            "productDTO": {"productId": "prod1"},
            "quantity": 1
        },
        {
            "productDTO": {"productId": "prod2"},
            "quantity": 1
        }
    ]
url = "http://localhost:8181/products/placeOrder?shippingAddress=shippingAddress"
try:
    response = requests.post(url=url, json=body, auth=auth, headers=headers)
    response.raise_for_status()
except requests.exceptions.HTTPError as error:
    print(error)
    sys.exit(error)
except requests.ConnectionError as error:
    print(error)
    sys.exit(error)
except requests.exceptions.Timeout as error:
    print(error)
    sys.exit(error)
except requests.exceptions.RequestException as error:
    print(error)
    sys.exit(error)

orderDTO = json.loads(response.text)
order_id = orderDTO["orderId"]
print(orderDTO)

time.sleep(10)

url = f"http://localhost:8181/products/order/{order_id}?newStatus=DELIVERING"
try:
    response = requests.put(url=url, auth=auth, headers=headers)
    response.raise_for_status()
except requests.exceptions.HTTPError as error:
    print(error)
    sys.exit(error)
except requests.ConnectionError as error:
    print(error)
    sys.exit(error)
except requests.exceptions.Timeout as error:
    print(error)
    sys.exit(error)
except requests.exceptions.RequestException as error:
    print(error)
    sys.exit(error)
print(response.text)


url = f"http://localhost:8181/products/orderStatus/{order_id}"
try:
    response = requests.get(url=url, auth=auth, headers=headers)
    response.raise_for_status()
except requests.exceptions.HTTPError as error:
    print(error)
    sys.exit(error)
except requests.ConnectionError as error:
    print(error)
    sys.exit(error)
except requests.exceptions.Timeout as error:
    print(error)
    sys.exit(error)
except requests.exceptions.RequestException as error:
    print(error)
    sys.exit(error)
print(f"New status = {response.text}")

time.sleep(10)

url = f"http://localhost:8181/products/order/{order_id}?newStatus=DELIVERED"
try:
    response = requests.put(url=url, auth=auth, headers=headers)
    response.raise_for_status()
except requests.exceptions.HTTPError as error:
    print(error)
    sys.exit(error)
except requests.ConnectionError as error:
    print(error)
    sys.exit(error)
except requests.exceptions.Timeout as error:
    print(error)
    sys.exit(error)
except requests.exceptions.RequestException as error:
    print(error)
    sys.exit(error)
print(response.text)

url = f"http://localhost:8181/products/orderStatus/{order_id}"
try:
    response = requests.get(url=url, auth=auth, headers=headers)
    response.raise_for_status()
except requests.exceptions.HTTPError as error:
    print(error)
    sys.exit(error)
except requests.ConnectionError as error:
    print(error)
    sys.exit(error)
except requests.exceptions.Timeout as error:
    print(error)
    sys.exit(error)
except requests.exceptions.RequestException as error:
    print(error)
    sys.exit(error)
print(f"New status = {response.text}")