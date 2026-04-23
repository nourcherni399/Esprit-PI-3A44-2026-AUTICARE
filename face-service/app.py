from __future__ import annotations

import base64
from datetime import datetime, timezone
from typing import Any, Dict

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel

try:
    from insightface.app import FaceAnalysis
except Exception as exc:  # pragma: no cover
    raise RuntimeError("insightface non disponible. Installez requirements.txt") from exc


app = FastAPI(title="AutiCare Face Service", version="1.0.0")
_face_app: FaceAnalysis | None = None


class VerifyRequest(BaseModel):
    template: Dict[str, Any]
    probe_image_base64: str


def _error(code: str, message: str, status: int = 400) -> HTTPException:
    return HTTPException(status_code=status, detail={"errorCode": code, "message": message})


def _get_face_app() -> FaceAnalysis:
    global _face_app
    if _face_app is None:
        _face_app = FaceAnalysis(name="buffalo_l")
        _face_app.prepare(ctx_id=-1, det_size=(640, 640))
    return _face_app


def _decode_image_bytes(raw: bytes) -> np.ndarray:
    if not raw:
        raise _error("INVALID_IMAGE", "Image vide.")
    arr = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise _error("INVALID_IMAGE", "Image invalide.")
    return img


def _extract_single_embedding(img: np.ndarray) -> np.ndarray:
    faces = _get_face_app().get(img)
    if len(faces) == 0:
        raise _error("NO_FACE", "Aucun visage détecté.")
    if len(faces) > 1:
        raise _error("MULTIPLE_FACES", "Plusieurs visages détectés.")
    emb = np.array(faces[0].embedding, dtype=np.float32)
    if emb.ndim != 1 or emb.size == 0:
        raise _error("INVALID_TEMPLATE", "Embedding invalide.")
    return emb


def _normalize(vec: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(vec)
    if norm <= 1e-12:
        raise _error("INVALID_TEMPLATE", "Embedding de norme nulle.")
    return vec / norm


def _cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    na = _normalize(a)
    nb = _normalize(b)
    sim = float(np.dot(na, nb))
    # clamp safety
    if sim < 0.0:
        return 0.0
    if sim > 1.0:
        return 1.0
    return sim


@app.exception_handler(HTTPException)
async def _http_exc_handler(_, exc: HTTPException):
    detail = exc.detail if isinstance(exc.detail, dict) else {"errorCode": "HTTP_ERROR", "message": str(exc.detail)}
    return fastapi_json(exc.status_code, detail)


def fastapi_json(status_code: int, payload: Dict[str, Any]):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=status_code, content=payload)


@app.get("/health")
def health() -> Dict[str, bool]:
    return {"ok": True}


@app.post("/enroll")
async def enroll(image: UploadFile = File(...)) -> Dict[str, Any]:
    raw = await image.read()
    img = _decode_image_bytes(raw)
    emb = _extract_single_embedding(img)
    norm = float(np.linalg.norm(emb))
    emb_n = _normalize(emb)
    return {
        "version": "insightface-v1",
        "embedding": emb_n.astype(float).tolist(),
        "norm": norm,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/verify")
def verify(req: VerifyRequest) -> Dict[str, float]:
    if req.template is None or "embedding" not in req.template:
        raise _error("INVALID_TEMPLATE", "Template biométrique invalide.")
    try:
        enrolled = np.array(req.template["embedding"], dtype=np.float32)
    except Exception:
        raise _error("INVALID_TEMPLATE", "Template biométrique invalide.")
    if enrolled.ndim != 1 or enrolled.size == 0:
        raise _error("INVALID_TEMPLATE", "Template biométrique invalide.")
    try:
        raw_probe = base64.b64decode(req.probe_image_base64, validate=True)
    except Exception:
        raise _error("INVALID_IMAGE", "Image de vérification invalide.")
    probe_img = _decode_image_bytes(raw_probe)
    probe_emb = _extract_single_embedding(probe_img)
    sim = _cosine_similarity(enrolled, probe_emb)
    return {"similarity": sim}
