from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from aiortc import RTCPeerConnection, RTCSessionDescription, VideoStreamTrack
from aiortc.contrib.media import MediaBlackhole
import cv2
import numpy as np
import asyncio


@asynccontextmanager
async def lifespan(app: FastAPI):
    global pcs

    pcs = set()
    yield

    for pc in pcs:
        await pc.close()
    pcs.clear()


app = FastAPI(lifespan=lifespan)
pcs = set()

class VideoDisplayTrack(VideoStreamTrack):
    def __init__(self, track):
        super().__init__()
        self.track = track

    async def recv(self):
        frame = await self.track.recv()

        img = frame.to_ndarray(format="bgr24")
        cv2.imshow("WebRTC Stream", img)
        cv2.waitKey(1)

        return frame


@app.post("/offer")
async def offer(request: Request):
    params = await request.json()
    offer = RTCSessionDescription(sdp=params["sdp"], type=params["type"])

    pc = RTCPeerConnection()
    pcs.add(pc)

    @pc.on("track")
    def on_track(track):
        print(f"Track received: {track.kind}")
        if track.kind == "video":
            display = VideoDisplayTrack(track)
            pc.addTrack(display)

    print(offer)
    try:
        await pc.setRemoteDescription(offer)
    except Exception as e:
        print("Error setting remote description:", e)
    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)

    return JSONResponse({
        "sdp": pc.localDescription.sdp,
        "type": pc.localDescription.type
    })


