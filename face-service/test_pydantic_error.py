from fastapi import FastAPI
from pydantic import BaseModel
from fastapi.testclient import TestClient

app = FastAPI()

class IdentifyCandidate(BaseModel):
    key: str
    template: dict

class IdentifyRequest(BaseModel):
    probe_image_base64: str
    candidates: list[IdentifyCandidate]

@app.post('/identify')
def identify(req: IdentifyRequest):
    return {'ok': True}

client = TestClient(app)

print('Empty body:', client.post('/identify').json())
print('Missing probe:', client.post('/identify', json={'candidates': []}).json())
print('Missing candidates:', client.post('/identify', json={'probe_image_base64': '123'}).json())
print('Malformed JSON:', client.post('/identify', data='{\"probe_image_base64\":\"123\", \"candidates\":[', headers={'Content-Type':'application/json'}).json())
