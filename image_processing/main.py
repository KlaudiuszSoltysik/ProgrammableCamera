from fastapi import FastAPI
from aiortc import RTCPeerConnection, RTCSessionDescription, MediaStreamTrack, RTCConfiguration, RTCIceServer
from contextlib import asynccontextmanager
import cv2
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


class VideoStreamTrack(MediaStreamTrack):
    kind = "video"

    def __init__(self, track):
        super().__init__()
        self.track = track

    async def recv(self):
        frame = await self.track.recv()

        img = frame.to_ndarray(format="bgr24")
        resized = cv2.resize(img, (360, 640))
        rotated = cv2.rotate(resized, cv2.ROTATE_90_CLOCKWISE)

        cv2.imshow("Video", rotated)
        cv2.waitKey(1)

        return frame


@app.post("/offer")
async def offer_sdp(offer: dict):
    pc = RTCPeerConnection()
    pcs.add(pc)

    @pc.on("track")
    def on_track(track):
        if track.kind == "video":
            pc.addTrack(VideoStreamTrack(track))

    # Set the remote SDP offer from the client
    offer_sdp = RTCSessionDescription(sdp=offer["sdp"], type=offer["type"])
    await pc.setRemoteDescription(offer_sdp)

    # Create an answer SDP and set it as the local description
    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)

    # Return the SDP answer to the client
    return {"sdp": pc.localDescription.sdp, "type": pc.localDescription.type}
