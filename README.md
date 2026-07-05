# 🛡️ Yapay Zeka Tabanlı İş Sağlığı ve Güvenliği (İSG) Kontrol Sistemi
**AI-Based Occupational Health and Safety (OHS) Control System**

[🌍 Click here for the English Version](#english-version)

[![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://python.org)
[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://java.com)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![YOLOv8](https://img.shields.io/badge/YOLOv8-00FFFF?style=for-the-badge&logo=yolo&logoColor=black)](https://github.com/ultralytics/ultralytics)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)

Bu proje, 100'den fazla personelin aktif olarak çalıştığı endüstriyel üretim sahalarında 6331 sayılı İSG Kanunu kapsamındaki Kişisel Koruyucu Donanım (KKD) denetimlerini otomatize etmek amacıyla geliştirilmiş, dağıtık mimariye sahip uçtan uca bir yapay zeka sistemidir.

---

## 🏗️ Proje Modülleri ve Mimari

Sistem; veri işleme ve yapay zeka, merkezi masaüstü kontrolü ve sahaya yönelik mobil bildirimler olmak üzere 3 temel modülden oluşmaktadır.

### 1. Veri Seti Mühendisliği ve Model Eğitimi (AI Server)
Endüstriyel sahalarda en sık kullanılan 10 farklı KKD sınıfı (baret, yelek, eldiven, maske vb.) için açık kaynaklardan çeşitli veri setleri toplanmıştır. Ham verilerdeki "sınıf dengesizliği" (class imbalance) problemini çözmek ve modelin öğrenme kalitesini artırmak için özel bir mimari kurgulanmıştır:
* **Veri Dengeleme:** Geliştirilen özel bir Python betiği (script) kullanılarak, toplanan devasa veriler filtrelenmiş ve oluşturulan 10 özel KKD sınıfının her biri için **tam 1000'er adet** görsel ayrılarak sınıf eşitsizliği tamamen ortadan kaldırılmıştır.
* **Eğitim ve Metrikler:** Hazırlanan bu dengeli veri seti (ISG_Final_Dataset2) üzerinde Ultralytics YOLOv8m modeli transfer öğrenme ile eğitilmiştir. Model; **mAP@0.5 = %96.9** ve **mAP@0.5:0.95 = %83.3** gibi endüstri standartlarının üzerinde bir başarıma ulaşmıştır.
* **AI Sunucusu (Backend):** Eğitilen model, Python üzerinden bir TCP Soket sunucusu olarak ayağa kaldırılmış ve 640px çözünürlükte ortalama **1.5 ms** gecikme ile gerçek zamanlı çıkarım (inference) yapacak hale getirilmiştir.

### 2. Masaüstü Kontrol Paneli (Desktop Client)

<img width="1118" height="631" alt="Ekran görüntüsü 2026-06-11 020040" src="https://github.com/user-attachments/assets/d98e3d82-1ca3-45f6-85c4-87946f0a58c1" />

Saha yöneticileri ve İSG uzmanları için **Java Swing** kullanılarak geliştirilmiş ana izleme merkezidir.
* Python AI sunucusu ile TCP/IP (Port 9999) üzerinden kesintisiz JSON tabanlı iletişim kurar.
* Kameralardan gelen anlık görüntüleri ve yapay zekanın çizdiği sınır kutularını (bounding boxes) ekranda gerçek zamanlı gösterir.
* Sektörel profillere (Şantiye, Kimya Tesisi, Lojistik vb.) göre kural setleri belirlenebilir. İhlal tespiti anında, ana çıkarım döngüsünü aksatmadan **Firebase Firestore** bulut günlüğüne asenkron olarak kayıt atar.

### 3. Mobil İstemci (Android App)

<img width="282" height="635" alt="Ekran görüntüsü 2026-06-11 020105" src="https://github.com/user-attachments/assets/a471d120-37e8-47ff-b8d2-3436f541aea2" />

Saha personelleri, amirler ve güvenlik görevlilerinin anlık müdahale edebilmesi için **Android (Java)** platformunda geliştirilmiştir.
* Firebase Firestore ile tam senkronize çalışır.
* Masaüstü veya AI sunucusu tarafından buluta yazılan bir "Kritik İhlal" durumunda, sahada devriye gezen yetkilinin cebine saniyeler içinde anlık bildirim (Push Notification) düşürür.
* Olay anının kanıtlarına (geçmiş ihlaller) mobil uygulama üzerinden anında ulaşılabilir.

---

## ⚙️ Kurulum ve Çalıştırma

**1. Gereksinimler**
```bash
git clone [https://github.com/ozgezaraozcelik/Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi.git](https://github.com/ozgezaraozcelik/Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi.git)
cd Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi/ai_server
python -m venv venv
venv\Scripts\activate # Windows için
pip install -r requirements.txt
```

**2. Yapılandırma (Şifreler ve Modeller)**

* **ai_server/ içindeki .env.example dosyasının adını .env olarak değiştirin.**

* **firebase-adminsdk.example.json dosyasının adını firebase-adminsdk.json yapıp içine Google Cloud üzerinden aldığınız kendi servis anahtarlarınızı girin.**

* **Eğittiğiniz best.pt ağırlık dosyasını ai_server/ dizinine ekleyin.**

**3. Sistemi Başlatma**

* **Yapay Zeka Sunucusu: ai_server/ dizininde python detector_server.py komutunu çalıştırın.**

* **Masaüstü Uygulaması: DesktopApp/ dizinindeki projeyi derleyip (javac SafetyApp.java) çalıştırın.**

* **Mobil Uygulama: MobileApp/ klasörünü Android Studio ile açarak derleyin.**


🙏 Teşekkür

Bu projenin vizyonunun oluşmasında ve mimarisinin kurulmasında değerli katkılarını esirgemeyen danışmanlarım Prof. Dr. Tuncay AYDOĞAN'a, Arş. Gör. Ahmet Bestami KÖSE'ye ve mentörüm Fatih Güler'e en içten teşekkürlerimi sunarım.

Geliştiren: Özge Zara Özçelik


# ###English Version

This project is a distributed, end-to-end artificial intelligence system designed to automate Personal Protective Equipment (PPE) inspections in industrial production sites with 100+ personnel, supporting compliance with Turkish OHS Law No. 6331.

# ###🏗️ Project Modules and Architecture
The system consists of 3 main modules: data processing/AI, central desktop control, and mobile field alerts.

### 1. Dataset Engineering and Model Training (AI Server)
Various open-source datasets were collected for the 10 most commonly used PPE classes in industrial fields (helmets, vests, gloves, masks, etc.). To prevent the "class imbalance" problem in raw data from corrupting the model's learning capabilities, a specific architecture was designed:

* **Data Balancing:** Using a custom-developed Python script, the massive amount of collected raw data was filtered, and exactly 1000 instances were selected for each of the 10 custom PPE classes. This completely eliminated class inequality.

* **Training & Metrics:** The Ultralytics YOLOv8m model was fine-tuned on this balanced dataset (ISG_Final_Dataset2). The model achieved outstanding performance with mAP@0.5 = 96.9% and mAP@0.5:0.95 = 83.3%.

* **AI Server (Backend):** The trained model was deployed as a TCP Socket server via Python, capable of performing real-time inference with an average latency of 1.5 ms at 640px resolution.

### 2. Desktop Control Panel (Desktop Client)

<img width="1118" height="631" alt="Ekran görüntüsü 2026-06-11 020040" src="https://github.com/user-attachments/assets/d98e3d82-1ca3-45f6-85c4-87946f0a58c1" />

* **Developed using Java Swing, this is the main monitoring hub for field managers and OHS experts.**

* **Establishes continuous JSON-based communication with the Python AI server via TCP/IP (Port 9999).**

* **Displays real-time camera feeds and AI-drawn bounding boxes directly on the screen.**

Rules can be set according to sectoral profiles (Construction Site, Chemical Plant, Logistics, etc.). Upon detecting a violation, the event is logged asynchronously to the Firebase Firestore cloud database without interrupting the main inference loop.

### 3. Mobile Client (Android App)

<img width="282" height="635" alt="Ekran görüntüsü 2026-06-11 020105" src="https://github.com/user-attachments/assets/a471d120-37e8-47ff-b8d2-3436f541aea2" />

* **Developed on the Android (Java) platform to enable instant response from field personnel, supervisors, and security guards.**

* **Fully synchronized with Firebase Firestore.**

* **Listens for asynchronous violation logs. If a "Critical Violation" is recorded by the desktop or AI server, an instant push notification is sent within seconds to the mobile device of the relevant personnel in the field.**

*Historical violations and proof of incidents can be accessed instantly through the mobile app.

⚙️ Installation & Setup

**1. Requirements**

````bash
git clone [https://github.com/ozgezaraozcelik/Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi.git](https://github.com/ozgezaraozcelik/Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi.git)
cd Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi/ai_server
python -m venv venv
venv\Scripts\activate # For Windows
pip install -r requirements.txt
````
**2. Configuration (Secrets & Models)**

* **Rename .env.example in ai_server/ to .env.**

* **Rename firebase-adminsdk.example.json to firebase-adminsdk.json and insert your own GCP service account keys.**

* **Place your trained best.pt weights file into the ai_server/ directory.**

**3. Starting the System**

* **AI Server:** Run python detector_server.py inside the ai_server/ directory.

* **Desktop App:** Compile (javac SafetyApp.java) and run the project inside DesktopApp/.

* **Mobile App:** Open the MobileApp/ folder with Android Studio and build the project.

🙏 Acknowledgements

I would like to express my sincere gratitude to my advisors, Prof. Dr. Tuncay AYDOĞAN and Res. Asst. Ahmet Bestami KÖSE, and my mentor Fatih Güler, for their valuable contributions and vision throughout the development of this project architecture.

Developed by Özge Zara Özçelik

