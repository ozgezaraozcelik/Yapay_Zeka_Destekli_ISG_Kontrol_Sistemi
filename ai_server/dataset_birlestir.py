import os
import random
import glob
import shutil


def valid_klasorunu_kurtar(eski_dataset_yolu, yeni_dir_yolu, ana_dataset_yolu):
    # Hatanın çözümü: Klasörün altındaki o gizli 'ISG_Final_Dataset' klasörüne doğrudan bağlanıyoruz!
    gercek_eski_yol = os.path.join(eski_dataset_yolu, 'ISG_Final_Dataset')

    valid_img_target = os.path.join(ana_dataset_yolu, 'valid', 'images')
    valid_lbl_target = os.path.join(ana_dataset_yolu, 'valid', 'labels')

    # Klasörü sıfırla
    if os.path.exists(valid_img_target): shutil.rmtree(valid_img_target)
    if os.path.exists(valid_lbl_target): shutil.rmtree(valid_lbl_target)
    os.makedirs(valid_img_target, exist_ok=True)
    os.makedirs(valid_lbl_target, exist_ok=True)

    print("🧹 Valid klasörü sıfırlandı. Büyük kurtarma operasyonu başladı...\n")

    # --- 1. ADIM: ESKİ SINIFLARI (0,1,2,3,4) ENJEKTE ETME ---
    # Eski zip içindeki hem train hem valid klasörlerini kontrol edip eski verileri topluyoruz
    eski_bulundu = False
    for split in ['valid', 'val', 'train']:
        src_old_img = os.path.join(gercek_eski_yol, split, 'images')
        src_old_lbl = os.path.join(gercek_eski_yol, split, 'labels')

        if os.path.exists(src_old_img):
            old_images = []
            for ext in ('*.jpg', '*.jpeg', '*.png', '*.JPG', '*.PNG'):
                old_images.extend(glob.glob(os.path.join(src_old_img, ext)))

            if old_images:
                eski_bulundu = True
                selected_old = random.sample(old_images, min(150, len(old_images)))
                for img_path in selected_old:
                    base_name = os.path.basename(img_path)
                    file_name = os.path.splitext(base_name)[0]
                    txt_path = os.path.join(src_old_lbl, f"{file_name}.txt")

                    shutil.copy(img_path, os.path.join(valid_img_target, base_name))
                    if os.path.exists(txt_path):
                        shutil.copy(txt_path, os.path.join(valid_lbl_target, f"{file_name}.txt"))
                print(
                    f"✅ Orijinal setin '{split}' klasöründen {len(selected_old)} adet eski sınıf resmi 'valid' klasörüne eklendi.")

    if not eski_bulundu:
        print("⚠️ Uyarı: Eski sınıflara ait resimler hala kopyalanamadı, klasör yapısını kontrol et kanka!")

    # --- 2. ADIM: YENİ SINIFLARI (5,6,7,8,9) ENJEKTE ETME ---
    datasets_to_add = [
        ("welding-helmet", 5),
        ("ear_protection", 6),
        ("toolbox", 7),
        ("safety_gloves", 8),
        ("protective_suit", 9)
    ]

    print("\n🔄 Yeni sınıflar (5,6,7,8,9) 'valid' klasörüne yanlarına ekleniyor...")
    for folder_name, new_class_id in datasets_to_add:
        full_path = os.path.join(yeni_dir_yolu, folder_name)
        src_new_img = os.path.join(full_path, 'train', 'images')
        src_new_lbl = os.path.join(full_path, 'train', 'labels')

        if os.path.exists(src_new_img):
            new_images = []
            for ext in ('*.jpg', '*.jpeg', '*.png', '*.JPG', '*.PNG'):
                new_images.extend(glob.glob(os.path.join(src_new_img, ext)))

            selected_new = random.sample(new_images, min(50, len(new_images)))

            for img_path in selected_new:
                base_name = os.path.basename(img_path)
                file_name = os.path.splitext(base_name)[0]
                txt_path = os.path.join(src_new_lbl, f"{file_name}.txt")

                shutil.copy(img_path, os.path.join(valid_img_target, base_name))

                if os.path.exists(txt_path):
                    try:
                        with open(txt_path, 'r', encoding='utf-8') as f:
                            lines = f.readlines()
                        new_lines = []
                        for line in lines:
                            parts = line.split()
                            if len(parts) > 0:
                                parts[0] = str(new_class_id)
                                new_lines.append(" ".join(parts) + "\n")
                        with open(os.path.join(valid_lbl_target, f"{file_name}.txt"), 'w', encoding='utf-8') as f:
                            f.writelines(new_lines)
                    except Exception as e:
                        print(f"⚠️ Dosya hatası: {e}")
            print(f"✅ {folder_name} (Sınıf {new_class_id}) -> {len(selected_new)} adet doğrulama resmi eklendi.")

    print("\n🚀 KESİN ZAFER! valid klasöründe artık 0'dan 9'a kadar tüm sınıflar bir arada!")


if __name__ == "__main__":
    ESKI_TEMIZ_SET = os.getenv("ESKI_TEMIZ_SET", "YOUR_PATH/ISG_Original_Eski")
    NEW_DIR = os.getenv("NEW_DIR", "YOUR_PATH/Newly_Added")
    MAIN_SET = os.getenv("MAIN_SET", "YOUR_PATH/ISG_Final_Dataset")

    if os.path.exists(MAIN_SET):
        valid_klasorunu_kurtar(ESKI_TEMIZ_SET, NEW_DIR, MAIN_SET)
    else:
        print("❌ Klasör yolları eşleşmedi kanka!")