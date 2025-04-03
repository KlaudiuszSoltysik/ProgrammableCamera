import cv2
from fastapi import FastAPI
from aiortc import RTCPeerConnection, RTCSessionDescription, MediaStreamTrack, RTCConfiguration, RTCIceServer
from contextlib import asynccontextmanager
from aiortc.contrib.media import MediaPlayer


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

        print(f"Received frame size: {img.shape}")

        cv2.imshow("Video", img)
        cv2.waitKey(1)

        return frame

@app.post("/offer")
async def offer_sdp(offer: dict):
    config = RTCConfiguration(
        iceServers=[RTCIceServer(urls="stun:stun.l.google.com:19302")]
    )
    pc = RTCPeerConnection(configuration=config)
    pcs.add(pc)

    @pc.on("track")
    def on_track(track):
        print(f"📹 Receiving video track: {track.kind}")
        if track.kind == "video":
            pc.addTrack(VideoStreamTrack(track))

    offer_sdp = RTCSessionDescription(sdp=offer["sdp"], type=offer["type"])
    await pc.setRemoteDescription(offer_sdp)

    # for sender in pc.getSenders():
    #     if sender.track.kind == "video":
    #         encodings = sender.getParameters().encodings
    #         for encoding in encodings:
    #             # Set maxBitrate and resolution scale
    #             encoding.maxBitrate = 2000000  # Set maximum bitrate to 2 Mbps
    #             encoding.scaleResolutionDownBy = 1.0  # No resolution scaling
    #         await sender.setParameters(sender.getParameters())

    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)

    return {"sdp": pc.localDescription.sdp, "type": pc.localDescription.type}