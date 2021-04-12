import sys
import requests
from requests.auth import HTTPBasicAuth

# run this code to add money to user wallet
headers = {'content-type': 'application/json'}
auth = HTTPBasicAuth('m.rossini@yopmail.com', 'password')
body = {
    "issuerId": "433333333333333333333334",
    "amount": 69420,
    "transactionMotivation": "ADMIN_RECHARGE"
}
url = "http://localhost:8183/wallets/444444444444444444444444/transactions/"
try:
    response = requests.put(url=url, json=body, auth=auth, headers=headers)
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
