import face_recognition
import numpy as np
import psycopg2
import io
import sys
import traceback

def encode_faces(user_id):
    file_path = rf"C:\Users\klaud\Documents\development\SmartHome\{user_id}.txt"

    with open(file_path, "r") as f:
        images_list = []
        for line in f:
            byte_array = np.array([int(x) for x in line.strip().split(",")], dtype=np.uint8)
            images_list.append(byte_array)

    encodings = []

    for image_bytes in images_list:
        try:
            image_array = np.frombuffer(image_bytes, dtype=np.uint8)
            image = face_recognition.load_image_file(io.BytesIO(image_array))

            face_enc = face_recognition.face_encodings(image)

            if face_enc:
                encodings.append(face_enc[0])
        except Exception as image_processing_error:
            print(f"Error processing image: {image_processing_error}")
            print(traceback.format_exc())
            return False

    if encodings:
        encodings_array = np.array(encodings, dtype=np.float32)
        binary_data = io.BytesIO()
        np.save(binary_data, encodings_array)
        binary_data.seek(0)

        try:
            params = {
                "host": "localhost",
                "port": 5432,
                "user": "postgres",
                "password": "admin",
                "dbname": "postgres"
            }
            conn = psycopg2.connect(**params)
            cursor = conn.cursor()
            cursor.execute('UPDATE "Users" SET "Face" = %s WHERE "Id" = %s', (binary_data.getvalue(), user_id))
            conn.commit()
            cursor.close()
            conn.close()

            return True
        except psycopg2.Error as db_error:
            print(f"Database error: {db_error}")
            print(traceback.format_exc())
            return False
        except Exception as e:
            print(f"An unexpected database error occurred: {e}")
            print(traceback.format_exc())
            return False
    else:
        print("No face encodings found.")
        return False


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python script.py user_id")
        sys.exit(1)

    try:
        user_id = int(sys.argv[1])

        success = encode_faces(user_id)

        if success:
            print("Face encodings successfully stored in the database.")
            sys.exit(0)
        else:
            print("Failed to encode and store face encodings.")
            sys.exit(1)

    except (ValueError, SyntaxError, psycopg2.Error) as e:
        print(f"Error: {e}")
        print(traceback.format_exc())
        sys.exit(1)

    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        print(traceback.format_exc())
        sys.exit(1)
