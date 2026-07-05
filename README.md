# 🛡️ Yapay Zeka Tabanlı İş Sağlığı ve Güvenliği (İSG) Kontrol Sistemi
**AI-Based Occupational Health and Safety (OHS) Control System**

[🌍 Click here for the English Version](#english-version)

[![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://python.org)
[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://java.com)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![YOLOv8](https://img.shields.io/badge/YOLOv8-00FFFF?style=for-the-badge&logo=yolo&logoColor=black)](https://github.com/ultralytics/ultralytics)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)

Bu proje, 100'den fazla personelin bulunduğu endüstriyel üretim sahalarında 6331 sayılı İSG Kanunu kapsamındaki Kişisel Koruyucu Donanım (KKD) denetimlerini otomatize etmek amacıyla geliştirilmiş, dağıtık mimariye sahip uçtan uca bir yapay zeka sistemidir.

---

## 🏗️ Proje Modülleri ve Mimari

Sistem; veri işleme, merkezi masaüstü kontrolü ve sahaya yönelik mobil bildirimler olmak üzere 3 temel modülden oluşmaktadır.

### 1. Veri Seti Mühendisliği ve Model Eğitimi (AI Server)
Endüstriyel sahalarda en sık kullanılan 10 farklı KKD sınıfı (baret, yelek, eldiven, maske vb.) için açık kaynaklardan çeşitli veri setleri toplanmıştır. Ancak ham verilerdeki "sınıf dengesizliği" (class imbalance) probleminin modelin öğrenme yeteneğini bozmaması için özel bir mimari kurgulanmıştır:
*   **Veri Dengeleme:** Geliştirilen özel bir Python betiği (script) kullanılarak, toplanan devasa veriler filtrelenmiş ve oluşturulan 10 özel KKD sınıfının her biri için **tam 1000'er adet** görsel ayrılmıştır. Sınıf eşitsizliği tamamen ortadan kaldırılmıştır.
*   **Eğitim:** Hazırlanan bu dengeli veri seti (ISG_Final_Dataset2) üzerinde Ultralytics YOLOv8m modeli eğitilmiş ve tüm sınıflarda eşit ağırlıklı, yüksek hassasiyetli bir öğrenme sağlanmıştır.
*   **AI Sunucusu:** Eğitilen model, Python üzerinden bir TCP Soket sunucusu olarak ayağa kaldırılmış ve saniyede milisaniyeler (1.5 ms) seviyesinde gerçek zamanlı çıkarım (inference) yapacak hale getirilmiştir.

### 2. Masaüstü Kontrol Paneli (Desktop Application)
Saha yöneticileri ve İSG uzmanları için **Java Swing** kullanılarak geliştirilmiş ana izleme merkezidir.
*   Python AI sunucusu ile TCP IP (Port 9999) üzerinden kesintisiz JSON tabanlı iletişim kurar.
*   Kameralardan gelen anlık görüntüleri ve yapay zekanın çizdiği sınır kutularını (bounding boxes) ekranda gerçek zamanlı gösterir.
*   Sektörel profillere (Şantiye, Kimya Tesisi vb.) göre kurallar belirlenebilir. Bir kural ihlali yaşandığında (örn: şantiyede baret takılmaması), ihlal anını asenkron olarak Firebase Firestore bulut günlüğüne kaydeder.

### 3. Mobil İstemci (Mobile Application)
Saha personelleri, amirler ve güvenlik görevlilerinin anlık müdahale edebilmesi için **Android (Java)** platformunda geliştirilmiştir.
*   Firebase Firestore ile tam senkronize çalışır.
*   Masaüstü veya AI sunucusu tarafından buluta yazılan bir "Kritik İhlal" durumunda, sahada devriye gezen yetkilinin cebine saniyeler içinde "Kritik Uyarı" bildirimi düşürür.
*   Olay anının kanıtlarına (geçmiş ihlaller) mobil uygulama üzerinden anında ulaşılabilir.

---

## 📊 Performans Metrikleri
*   **Model Doğruluğu:** mAP@0.5 = %96.9 | mAP@0.5:0.95 = %83.3
*   **Çıkarım Hızı (Inference):** 640x640px çözünürlükte ortalama 1.5 ms gecikme.

---

## ⚙️ Kurulum ve Çalıştırma

**1. Gereksinimler**
```bash
git clone https://github.com/ozgezaraozcelik/Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi.git
cd Yapay_Zeka_Destekli_ISG_Kontrol_Sistemi/ai_server
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
