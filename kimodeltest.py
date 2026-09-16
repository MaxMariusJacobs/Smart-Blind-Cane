from ultralytics import YOLO

model = YOLO("yolov8n.pt")
# Exportiert das Modell als optimierte Web-Version
model.export(format="onnx", imgsz=640, optimize=True)