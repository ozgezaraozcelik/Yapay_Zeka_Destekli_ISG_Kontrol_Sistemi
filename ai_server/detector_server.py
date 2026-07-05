import cv2
import socket
import struct
import json
import os
import numpy as np
from ultralytics import YOLO
import datetime
import time
import threading  # MEVCUT YAPIYI KORUMAK İÇİN ASENKRON THREAD EKLEMESİ
import firebase_admin
from firebase_admin import credentials, firestore
from dotenv import load_dotenv

load_dotenv()

# ==========================================
# 1. NETWORK & PORT CONFIGURATION
# ==========================================
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "9999"))
MODEL_PPE_PATH = os.getenv("MODEL_PPE_PATH", "best.pt")
MODEL_PERSON_PATH = os.getenv("MODEL_PERSON_PATH", "yolov8n.pt")

# ==========================================
# EXTRA: FIREBASE INITIALIZATION
# ==========================================
FIREBASE_CONFIG_PATH = os.getenv("FIREBASE_CREDENTIALS_PATH", "firebase-adminsdk.json")
firebase_initialized = False
db = None

if os.path.exists(FIREBASE_CONFIG_PATH):
    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(FIREBASE_CONFIG_PATH)
            firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("[SUCCESS] Masaüstü Sunucusu Firebase Firestore bağlantısı başarıyla sağlandı.")
        firebase_initialized = True
    except Exception as e:
        print(f"[WARNING] Firebase başlatılamadı (Sistem lokal modda çalışmaya devam edecek): {e}")
else:
    print(f"[WARNING] '{FIREBASE_CONFIG_PATH}' bulunamadı. Sistem lokal modda çalışmaya devam edecek.")

# Veri tabanını gereksiz logla şişirmemek için zaman takibi mekanizması (Cooldown)
last_push_times = {}
PUSH_COOLDOWN = 10  # Aynı ihlal türü maksimum 10 saniyede bir Firebase'e yazılır

# ==========================================
# 2. AI MODEL INITIALIZATION
# ==========================================
print("[INFO] Masaüstü yapay zeka modelleri yükleniyor...")
# 10 sınıflı optimize edilmiş yeni model dosyası aktif ediliyor
model_ppe = YOLO(MODEL_PPE_PATH)
model_person = YOLO(MODEL_PERSON_PATH)
print("[INFO] best.pt sınıfları:", model_ppe.names)

# Java JSON anahtarı -> model etiketi (best.pt names ile birebir, alt string YOK)
RULE_BY_CLASS = {
    "mask": "check_mask",
    "helmet": "check_helmet",
    "belt": "check_belt",
    "vest": "check_vest",
    "glasses": "check_glasses",
    "goggles": "check_glasses",
    "protective_suit": "check_suit",
    "safety_gloves": "check_gloves",
    "toolbox": "check_toolbox",
    "welding_helmet": "check_welding",
    "ear_protection": "check_ear",
}

# best.pt içinde ear_protection sınıf kimliği
EAR_CLASS_ID = 6
PREDICT_CONF = 0.10
PREDICT_CONF_EAR = 0.03  # kulak için agresif YOLO ön filtresi
EAR_IMGSZ = 960            # küçük nesne için daha yüksek çözünürlük

# Soket bağlantısının yapılandırılması ve dinleme moduna alınması
server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server_socket.bind((HOST, PORT))
server_socket.listen(1)
print(f"[INFO] Python Masaüstü Sunucusu hazır (Port: {PORT}). Java Desktop bekleniyor...")

conn, addr = server_socket.accept()
print(f"[CONNECTED] Java Desktop uygulaması başarıyla bağlandı: {addr}")

cap = cv2.VideoCapture(0)
rules = {}

# ==========================================
# 3. CRITICAL CONFIGURATION: CLASS THRESHOLDS
# ==========================================
THRESHOLDS = {
    "mask": 0.28,
    "helmet": 0.15,
    "vest": 0.30,
    "glasses": 0.30,
    "goggles": 0.30,
    "belt": 0.25,
    "protective_suit": 0.20,
    "safety_gloves": 0.20,
    "toolbox": 0.20,
    "welding_helmet": 0.20,
    "ear_protection": 0.01,
}

_frame_counter = 0


# ==========================================
# EXTRA: ASENKRON FIREBASE METOTLARI
# ==========================================
def push_to_firebase_async(ihlal_turu):
    """Masaüstü ihlallerini Firebase Firestore bulut veritabanına asenkron kaydeder."""
    if not firebase_initialized:
        return
    now = time.time()
    if now - last_push_times.get(ihlal_turu, 0) > PUSH_COOLDOWN:
        try:
            db.collection("Ihlaller").add({
                "tur": ihlal_turu,
                "tarih": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "durum": "Kritik",
                "kaynak": "Masaustu_Uygulamasi"
            })
            last_push_times[ihlal_turu] = now
            print(f"[FIREBASE DESKTOP LOG] Buluta İhlal Bildirimi Yazıldı: {ihlal_turu}")
        except Exception as e:
            print(f"[ERROR] Firebase Firestore servis hatası: {e}")

def trigger_firebase(ihlal_turu):
    """FPS takılmasını önlemek için Firebase işlemini arka planda (thread) tetikler."""
    threading.Thread(target=push_to_firebase_async, args=(ihlal_turu,)).start()


def draw_alert(frame, text, y):
    """Görüntü üzerine kırmızı alarm paneli ve uyarı metni çizer."""
    cv2.rectangle(frame, (5, y - 25), (400, y + 5), (0, 0, 255), -1)
    cv2.putText(frame, text, (10, y),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)


def should_draw_class(cls_name, rules):
    """Model etiketini Java kural anahtarıyla tam eşleştirir (welding_helmet != helmet)."""
    rule_key = RULE_BY_CLASS.get(cls_name)
    return rule_key is not None and bool(rules.get(rule_key))


def class_detected(detected_set, class_name):
    """İhlal kontrolü: yalnızca tam sınıf adı (alt string değil)."""
    return class_name in detected_set


def process_boxes(boxes, model_names, rules, detected_classes, annotated_frame, raw_hits,
                  x_offset=0, y_offset=0):
    """Tespit kutularını işler; kırpılmış bölge için ofset uygular."""
    if boxes is None or len(boxes) == 0:
        return
    for box in boxes:
        cls_id = int(box.cls[0])
        raw_cls_name = model_names[cls_id]
        cls_name = raw_cls_name.lower()
        confidence = float(box.conf[0])
        raw_hits.append(f"{cls_name}@{confidence:.2f}")

        required_conf = THRESHOLDS.get(cls_name, 0.20)
        if confidence < required_conf:
            continue

        detected_classes.append(cls_name)
        if not should_draw_class(cls_name, rules):
            continue

        x1, y1, x2, y2 = map(int, box.xyxy[0])
        x1 += x_offset
        x2 += x_offset
        y1 += y_offset
        y2 += y_offset
        color = (0, 255, 255) if cls_name == "ear_protection" else (0, 255, 0)
        cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, 2)
        label = f"{raw_cls_name} {confidence:.2f}"
        cv2.putText(annotated_frame, label, (x1, max(y1 - 10, 15)),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)


def run_ear_boost(frame, rules, detected_classes, annotated_frame, raw_hits):
    """Kulak koruyucu: kafa bölgesi + sadece sınıf 6 ile ikinci geçiş."""
    if not rules.get("check_ear"):
        return

    h, w = frame.shape[:2]
    head_h = max(int(h * 0.55), 120)
    head_crop = frame[0:head_h, :]

    passes = [
        (frame, 0, 0, "tam_kare"),
        (head_crop, 0, 0, "kafa_kirpimi"),
    ]
    for img, x_off, y_off, tag in passes:
        ear_res = model_ppe.predict(
            img,
            conf=PREDICT_CONF_EAR,
            imgsz=EAR_IMGSZ,
            verbose=False,
            classes=[EAR_CLASS_ID],
        )
        boxes = ear_res[0].boxes
        if boxes is not None and len(boxes) > 0:
            if _frame_counter % 60 == 1:
                print(f"[DEBUG] Kulak geçişi ({tag}): {len(boxes)} kutu")
        process_boxes(
            boxes, model_ppe.names, rules, detected_classes, annotated_frame, raw_hits,
            x_offset=x_off, y_offset=y_off,
        )


# ==========================================
# 4. MAIN PROCESSING & INFERENCE LOOP
# ==========================================
try:
    while True:
        # 4.1. Java İstemcisinden Kural JSON Paketinin Alınması
        raw_msg_len = conn.recv(4)
        if not raw_msg_len:
            break

        msg_len = struct.unpack('>I', raw_msg_len)[0]

        data = b''
        while len(data) < msg_len:
            packet = conn.recv(msg_len - len(data))
            if not packet:
                break
            data += packet

        rules = json.loads(data.decode('utf-8'))
        _frame_counter += 1
        if _frame_counter % 60 == 1:
            print("Java'dan gelen güncel kurallar:", rules)
        # 4.2. Görüntü Yakalama ve Ön İşleme
        ret, frame = cap.read()
        if not ret:
            break

        frame_resized = cv2.resize(frame, (640, 480))
        results_ppe = model_ppe.predict(frame_resized, conf=PREDICT_CONF, verbose=False)
        annotated_frame = frame_resized.copy()

        detected_classes = []
        raw_hits = []

        # 4.3. Genel tespit + kulak için ek güçlendirilmiş geçiş
        process_boxes(
            results_ppe[0].boxes, model_ppe.names, rules,
            detected_classes, annotated_frame, raw_hits,
        )
        run_ear_boost(frame_resized, rules, detected_classes, annotated_frame, raw_hits)

        detected_set = set(detected_classes)
        if _frame_counter % 60 == 1 and raw_hits:
            ear_hits = [h for h in raw_hits if h.startswith("ear_protection")]
            print("[DEBUG] Ham tespitler:", ", ".join(raw_hits[:12]))
            if rules.get("check_ear"):
                print("[DEBUG] Kulak ham:", ", ".join(ear_hits) if ear_hits else "YOK (model bu karede üretmedi)")

        # 4.4. Dinamik İhlal Analizi ve Görsel Uyarı Yönetimi
        y_offset = 40
        violations = []

        if rules.get("check_mask") and not class_detected(detected_set, "mask"):
            draw_alert(annotated_frame, "UYARI: Maske Yok!", y_offset)
            trigger_firebase("Maske Yok!")
            violations.append("Maske Yok!")
            y_offset += 40

        if rules.get("check_helmet") and not class_detected(detected_set, "helmet"):
            draw_alert(annotated_frame, "UYARI: Baret Yok!", y_offset)
            trigger_firebase("Baret Yok!")
            violations.append("Baret Yok!")
            y_offset += 40

        if rules.get("check_vest") and not class_detected(detected_set, "vest"):
            draw_alert(annotated_frame, "UYARI: Yelek Yok!", y_offset)
            trigger_firebase("Yelek Yok!")
            violations.append("Yelek Yok!")
            y_offset += 40

        if rules.get("check_glasses") and not (
                class_detected(detected_set, "glasses") or class_detected(detected_set, "goggles")):
            draw_alert(annotated_frame, "UYARI: Gozluk Yok!", y_offset)
            trigger_firebase("Gözlük Yok!")
            violations.append("Gözlük Yok!")
            y_offset += 40

        if rules.get("check_belt") and not class_detected(detected_set, "belt"):
            draw_alert(annotated_frame, "UYARI: Kemer Yok!", y_offset)
            trigger_firebase("Kemer Yok!")
            violations.append("Kemer Yok!")
            y_offset += 40

        if rules.get("check_suit") and not class_detected(detected_set, "protective_suit"):
            draw_alert(annotated_frame, "UYARI: Koruyucu Elbise Yok!", y_offset)
            trigger_firebase("Koruyucu Elbise Yok!")
            violations.append("Koruyucu Elbise Yok!")
            y_offset += 40

        if rules.get("check_gloves") and not class_detected(detected_set, "safety_gloves"):
            draw_alert(annotated_frame, "UYARI: Eldiven Yok!", y_offset)
            trigger_firebase("Eldiven Yok!")
            violations.append("Eldiven Yok!")
            y_offset += 40

        if rules.get("check_toolbox") and not class_detected(detected_set, "toolbox"):
            draw_alert(annotated_frame, "UYARI: Alet Cantasi Yok!", y_offset)
            trigger_firebase("Alet Çantası Yok!")
            violations.append("Alet Çantası Yok!")
            y_offset += 40

        if rules.get("check_welding") and not class_detected(detected_set, "welding_helmet"):
            draw_alert(annotated_frame, "UYARI: Kaynak Maskesi Yok!", y_offset)
            trigger_firebase("Kaynak Maskesi Yok!")
            violations.append("Kaynak Maskesi Yok!")
            y_offset += 40

        if rules.get("check_ear") and not class_detected(detected_set, "ear_protection"):
            draw_alert(annotated_frame, "UYARI: Kulak Koruyucu Yok!", y_offset)
            trigger_firebase("Kulak Koruyucu Yok!")
            violations.append("Kulak Koruyucu Yok!")
            y_offset += 40

        # 4.5. İşlenmiş Matris Verisinin Stream Edilmesi (Java Transfer)
        _, img_encoded = cv2.imencode('.jpg', annotated_frame)
        data = img_encoded.tobytes()
        size = len(data)

        conn.sendall(struct.pack(">I", size) + data)

        # 4.6. Canlı ihlal listesi (Java alert paneli – görüntüden sonra, ek paket)
        viol_payload = "\n".join(violations).encode('utf-8')
        conn.sendall(struct.pack(">I", len(viol_payload)) + viol_payload)

except Exception as e:
    print(f"[ERROR] Sunucu çalışma döngüsünde kritik hata: {e}")
finally:
    cap.release()
    conn.close()
    server_socket.close()
    print("[INFO] Tüm kaynaklar güvenli bir şekilde serbest bırakıldı.")