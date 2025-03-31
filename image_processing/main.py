from fastapi import FastAPI, WebSocket
import json
import cv2
import numpy as np
import threading


def process_image(image_bytes):
    nparr = np.frombuffer(image_bytes, np.uint8)

    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if img is not None:
        cv2.imshow("Streaming", img)
        cv2.waitKey(1)


app = FastAPI()

@app.websocket("/camera")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()

    websocket.max_size = 10 * 1024 * 1024

    data = await websocket.receive_text()

    token = json.loads(data)["token"]

    while True:
        image_bytes = await websocket.receive_bytes()

        thread = threading.Thread(target=process_image, args=(image_bytes,))
        thread.start()


# uvicorn main:app --host 0.0.0.0 --port 8000 --reload