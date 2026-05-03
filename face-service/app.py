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


class IdentifyCandidate(BaseModel):
    key: str
    template: Dict[str, Any]


class IdentifyRequest(BaseModel):
    probe_image_base64: str
    candidates: list[IdentifyCandidate]


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
    # In real webcam scenes, background portraits can appear; pick the largest face.
    if len(faces) > 1:
        try:
            faces = sorted(
                faces,
                key=lambda f: float((f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1])),
                reverse=True,
            )
        except Exception:
            # Keep original order if bbox metadata is unavailable/invalid.
            pass
    emb = np.array(faces[0].embedding, dtype=np.float32)
    if emb.ndim != 1 or emb.size == 0:
        raise _error("INVALID_TEMPLATE", "Embedding invalide.")
    return emb


def _normalize(vec: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(vec)
    if norm <= 1e-12:
        raise _error("INVALID_TEMPLATE", "Embedding de norme nulle.")
    return vec / norm


def _embeddings_from_template(template: Dict[str, Any]) -> list[np.ndarray]:
    out: list[np.ndarray] = []
    if not template:
        return out
    if "embeddings" in template and isinstance(template["embeddings"], list):
        for item in template["embeddings"]:
            try:
                vec = np.array(item, dtype=np.float32)
                if vec.ndim == 1 and vec.size > 0:
                    out.append(vec)
            except Exception:
                continue
    if "embedding" in template:
        try:
            vec = np.array(template["embedding"], dtype=np.float32)
            if vec.ndim == 1 and vec.size > 0:
                out.append(vec)
        except Exception:
            pass
    return out


def _probe_variant_embeddings(img: np.ndarray) -> list[np.ndarray]:
    """
    Fast path first:
    - try original image
    - if no face, try mirrored image
    Only then run heavier preprocessing fallbacks.
    """
    embs: list[np.ndarray] = []
    variants_fast: list[np.ndarray] = [img]
    try:
        variants_fast.append(cv2.flip(img, 1))
    except Exception:
        pass
    for variant in variants_fast:
        try:
            embs.append(_extract_single_embedding(variant))
            if embs:
                return embs
        except HTTPException:
            continue
        except Exception:
            continue

    variants_fallback: list[np.ndarray] = []
    try:
        h, w = img.shape[:2]
        m = min(h, w)
        if m < 480:
            scale = 480.0 / float(m)
            up = cv2.resize(
                img,
                (max(1, int(round(w * scale))), max(1, int(round(h * scale)))),
                interpolation=cv2.INTER_CUBIC,
            )
            variants_fallback.append(up)
            variants_fallback.append(cv2.flip(up, 1))
    except Exception:
        pass
    try:
        # Mild contrast enhancement for low-light captures.
        lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        l2 = clahe.apply(l)
        enhanced = cv2.cvtColor(cv2.merge((l2, a, b)), cv2.COLOR_LAB2BGR)
        variants_fallback.append(enhanced)
        variants_fallback.append(cv2.flip(enhanced, 1))
    except Exception:
        pass

    for variant in variants_fallback:
        try:
            embs.append(_extract_single_embedding(variant))
        except HTTPException:
            continue
        except Exception:
            # Prevent internal variant-processing errors from bubbling as HTTP 500.
            continue
    return embs


def _cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    if a.ndim != 1 or b.ndim != 1 or a.size != b.size:
        raise _error("INCOMPATIBLE_TEMPLATE", "Template biométrique incompatible (dimension différente).")
    na = _normalize(a)
    nb = _normalize(b)
    sim = float(np.dot(na, nb))
    # clamp safety
    if sim < 0.0:
        return 0.0
    if sim > 1.0:
        return 1.0
    return sim


def _normalized_or_none(vec: np.ndarray) -> np.ndarray | None:
    try:
        return _normalize(vec)
    except HTTPException:
        return None


@app.exception_handler(HTTPException)
async def _http_exc_handler(_, exc: HTTPException):
    detail = exc.detail if isinstance(exc.detail, dict) else {"errorCode": "HTTP_ERROR", "message": str(exc.detail)}
    return fastapi_json(exc.status_code, detail)


@app.exception_handler(Exception)
async def _unhandled_exc_handler(_, __: Exception):
    return fastapi_json(500, {"errorCode": "SERVICE_ERROR", "message": "Erreur interne du service Face ID."})


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
        "embeddings": [emb_n.astype(float).tolist()],
        "norm": norm,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/verify")
def verify(req: VerifyRequest) -> Dict[str, float]:
    if req.template is None:
        raise _error("INVALID_TEMPLATE", "Template biométrique invalide.")
    enrolled_list = _embeddings_from_template(req.template)
    if not enrolled_list:
        raise _error("INVALID_TEMPLATE", "Template biométrique invalide.")
    try:
        raw_probe = base64.b64decode(req.probe_image_base64, validate=True)
    except Exception:
        raise _error("INVALID_IMAGE", "Image de vérification invalide.")
    probe_img = _decode_image_bytes(raw_probe)
    probe_embs = _probe_variant_embeddings(probe_img)
    if not probe_embs:
        raise _error("NO_FACE", "Aucun visage détecté.")

    enrolled_norm = [x for x in (_normalized_or_none(e) for e in enrolled_list) if x is not None]
    probe_norm = [x for x in (_normalized_or_none(p) for p in probe_embs) if x is not None]
    sims: list[float] = []
    for enrolled in enrolled_norm:
        for pe in probe_norm:
            try:
                sims.append(float(np.dot(enrolled, pe)))
            except HTTPException as exc:
                # Ignore legacy templates with other embedding dimensions.
                if isinstance(exc.detail, dict) and exc.detail.get("errorCode") == "INCOMPATIBLE_TEMPLATE":
                    continue
                raise
    if not sims:
        raise _error("INCOMPATIBLE_TEMPLATE", "Template biométrique incompatible (dimension différente).")
    sim = max(sims)
    return {"similarity": sim}


@app.post("/identify")
def identify(req: IdentifyRequest) -> Dict[str, Any]:
    """One probe decode + detection; compare to many enrolled templates (fast login path)."""
    try:
        raw_probe = base64.b64decode(req.probe_image_base64, validate=True)
    except Exception:
        raise _error("INVALID_IMAGE", "Image de vérification invalide.")
    probe_img = _decode_image_bytes(raw_probe)
    probe_embs = _probe_variant_embeddings(probe_img)
    if not probe_embs:
        raise _error("NO_FACE", "Aucun visage détecté.")

    probe_norm = [x for x in (_normalized_or_none(p) for p in probe_embs) if x is not None]
    best_key: str | None = None
    best_sim = -1.0
    for c in req.candidates:
        enrolled_list = _embeddings_from_template(c.template)
        if not enrolled_list:
            continue
        enrolled_norm = [x for x in (_normalized_or_none(e) for e in enrolled_list) if x is not None]
        for enrolled in enrolled_norm:
            for pe in probe_norm:
                try:
                    if enrolled.size != pe.size:
                        raise _error("INCOMPATIBLE_TEMPLATE", "Template biométrique incompatible (dimension différente).")
                    sim = float(np.dot(enrolled, pe))
                    if sim < 0.0:
                        sim = 0.0
                    elif sim > 1.0:
                        sim = 1.0
                except HTTPException as exc:
                    if isinstance(exc.detail, dict) and exc.detail.get("errorCode") == "INCOMPATIBLE_TEMPLATE":
                        continue
                    raise
                if sim > best_sim:
                    best_sim = sim
                    best_key = c.key

    return {"bestKey": best_key, "similarity": float(best_sim)}