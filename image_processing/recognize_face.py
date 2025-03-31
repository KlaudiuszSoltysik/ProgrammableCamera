import face_recognition
import numpy as np

known_encodings = np.load(saved_data, allow_pickle=True)
image = face_recognition.load_image_file(image_path)
encodings = face_recognition.face_encodings(image)

if encodings:
    matches = face_recognition.compare_faces(known_encodings, encodings[0])
    print("Match!" if any(matches) else "No match!")
else:
    print("No face found!")