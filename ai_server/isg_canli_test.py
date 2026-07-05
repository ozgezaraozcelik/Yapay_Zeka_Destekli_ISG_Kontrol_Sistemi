import cv2
import datetime
import os
import firebase_admin
from firebase_admin import credentials, firestore
from ultralytics import YOLO
from dotenv import load_dotenv

load_dotenv()

# ==========================================
# 1. FIREBASE & CLOUD DATABASE CONFIGURATION
# ==========================================
FIREBASE_CONFIG_PATH = os.getenv("FIREBASE_CREDENTIALS_PATH", "firebase-adminsdk.json")
MODEL_PPE_PATH = os.getenv("MODEL_PPE_PATH", "best.pt")

db = None

if os.path.exists(FIREBASE_CONFIG_PATH):
    try:
        cred = credentials.Certificate(FIREBASE_CONFIG_PATH)
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("[INFO] Firebase Firestore bağlantısı başarıyla kuruldu.")
    except Exception as e:
        print(f"[ERROR] Firebase başlatılırken hata oluştu: {e}")
else:
    print(f"[WARNING] '{FIREBASE_CONFIG_PATH}' dosyası bulunamadı. Sistem veritabanı bağlantısı olmadan başlatılıyor.")

# ==========================================
# 2. AI MODEL INITIALIZATION & OPTIMIZATION
# ==========================================
model = YOLO(MODEL_PPE_PATH)

# ==========================================
# 3. CORE FUNCTIONS / DATABASE OPERATIONS
# ==========================================
def ihlal_kaydet(ihlal_turu):
    """
    Kamera tarafından tespit edilen İSG ihlallerini
    real-time olarak Firebase Firestore veritabanına kaydeder.
    """
    if db is not None and firebase_admin._apps:
        try:
            doc_ref = db.collection('Ihlaller').document()
            doc_ref.set({
                'tur': ihlal_turu,
                'tarih': datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                'durum': 'Tespit Edildi'
            })
            print(f"[INFO] Veritabanına Kaydedildi -> İhlal Türü: {ihlal_turu}")
        except Exception as e:
            print(f"[ERROR] Veritabanı kaydı sırasında hata oluştu: {e}")
    else:
        print(f"[WARNING] Veritabanı bağlantısı aktif değil. İhlal loglandı: {ihlal_turu}")

# ==========================================
# 4. REAL-TIME VIDEO PROCESSING & INFERENCE
# ==========================================
# Varsayılan video yakalama cihazının (Webcam) aktif edilmesi
cap = cv2.VideoCapture(0)

print("[INFO] Canlı kamera akışı başlatılıyor. Çıkış yapmak için 'q' tuşuna basınız.")

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        print("[ERROR] Kamera akışından görüntü alınamadı!")
        break

    # CPU Performans Optimizasyonu:
    # Girdi görüntüsü donanım yükünü azaltmak adına 640x480 boyutuna ölçeklendirilir.
    frame_resized = cv2.resize(frame, (640, 480))

    # Model Çıkarım (Inference) Ayarları:
    # conf=0.25 -> Güven eşiği %25 olarak sınırlandırılmıştır.
    # imgsz=320  -> Modelin resmi işleme boyutu 320px'e düşürülerek FPS artışı sağlanmıştır.
    results = model(frame_resized, conf=0.25, imgsz=320)

    # Tespit edilen nesne sınır çizgilerinin (Bounding Boxes) ana görüntü üzerine çizilmesi
    annotated_frame = results[0].plot()

    # Sunum ekranının kullanıcıya gösterilmesi
    cv2.imshow("Isg Anlik Kontrol Sistemi - Canli Test", annotated_frame)

    # 'q' tuşuna basılması durumunda döngünün sonlandırılması
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# Kaynakların serbest bırakılması ve pencerelerin kapatılması
cap.release()
cv2.destroyAllWindows()
print("[INFO] Kamera akışı ve tüm pencereler güvenli bir şekilde kapatıldı.")