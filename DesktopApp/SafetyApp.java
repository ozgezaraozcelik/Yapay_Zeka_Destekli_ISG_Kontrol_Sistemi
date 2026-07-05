import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

public class SafetyApp extends JFrame {

    private static final int MAX_ALERT_LOGS = 20;
    private static final long VIOLATION_LOG_COOLDOWN_MS = 3000;
    private static final int TOAST_VISIBLE_MS = 5000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FULL_TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JLabel imageLabel;
    private LiveToastBanner liveToastBanner;
    private JPanel toastRowWrap;
    private JPanel alertLogPanel;
    private JButton btnToggleHistory;
    private Timer toastHideTimer;

    private JPanel violationFeedPanel;
    private int kayitSayisi = 0;

    private long lastPopupHideMs = 0;

    private JCheckBox cbMask;
    private JCheckBox cbHelmet;
    private JCheckBox cbBelt;
    private JCheckBox cbVest;
    private JCheckBox cbGlasses;
    private JCheckBox cbSuit;
    private JCheckBox cbGloves;
    private JCheckBox cbToolbox;
    private JCheckBox cbWelding;
    private JCheckBox cbEar;


    private final Map<String, Long> lastViolationLogMs = new HashMap<>();

    private final String HOST = "127.0.0.1";
    private final int PORT = 9999;

    private volatile String currentJsonRules = "{}";

    public SafetyApp() {
        setTitle("ISG Akıllı KKD Denetim Sistemi");
        setSize(1440, 900);
        setMinimumSize(new Dimension(1200, 760));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BG_APP);

        initHistoryToggleButton();

        JPanel mainContent = new JPanel(new BorderLayout(0, 0));
        mainContent.setOpaque(false);
        mainContent.add(buildControlPanel(), BorderLayout.WEST);
        mainContent.add(buildCameraSection(), BorderLayout.CENTER);

        alertLogPanel = buildCommandCenterPanel();
        alertLogPanel.setVisible(false);
        mainContent.add(alertLogPanel, BorderLayout.EAST);

        add(buildTopHeader(), BorderLayout.NORTH);
        add(mainContent, BorderLayout.CENTER);
        add(buildBottomActionBar(), BorderLayout.SOUTH);

        updateRulesString();
        new Thread(this::connectToPythonServer).start();
        setVisible(true);
    }

    private JPanel buildTopHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_HEADER);
        header.setPreferredSize(new Dimension(0, 68));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, Theme.ACCENT_SAFETY),
                new EmptyBorder(14, 24, 14, 24)
        ));

        JPanel brandCol = new JPanel(new GridLayout(2, 1, 0, 3));
        brandCol.setOpaque(false);
        JLabel brandTitle = new JLabel("ISG Akıllı KKD Denetim Sistemi");
        brandTitle.setFont(Theme.FONT_DISPLAY.deriveFont(Font.BOLD, 22f));
        brandTitle.setForeground(Color.WHITE);
        JLabel brandSub = new JLabel("Yapay Zeka Destekli İş Güvenliği  ·  Gerçek Zamanlı Kamera Analizi");
        brandSub.setFont(Theme.FONT_CAPTION.deriveFont(12f));
        brandSub.setForeground(Theme.TEXT_ON_HEADER);
        brandCol.add(brandTitle);
        brandCol.add(brandSub);
        header.add(brandCol, BorderLayout.WEST);

        JPanel rightCol = new JPanel(new BorderLayout(16, 0));
        rightCol.setOpaque(false);

        JPanel statusCol = new JPanel(new GridLayout(2, 1, 0, 4));
        statusCol.setOpaque(false);
        JLabel liveStatus = new JLabel("CANLI İZLEME");
        liveStatus.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 11f));
        liveStatus.setForeground(Theme.ACCENT_SAFETY);
        liveStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel sysStatus = new JLabel("Sistem Hazır");
        sysStatus.setFont(Theme.FONT_CAPTION.deriveFont(11f));
        sysStatus.setForeground(new Color(0x86, 0xEF, 0xAC));
        sysStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        statusCol.add(liveStatus);
        statusCol.add(sysStatus);

        JButton btnPersonel = new JButton("Personeller");
        btnPersonel.setUI(new BasicButtonUI());
        btnPersonel.setFont(Theme.FONT_BODY_BOLD.deriveFont(Font.BOLD, 13f));
        btnPersonel.setForeground(Theme.BG_HEADER);
        btnPersonel.setBackground(Theme.ACCENT_SAFETY);
        btnPersonel.setOpaque(true);
        btnPersonel.setContentAreaFilled(true);
        btnPersonel.setBorderPainted(false);
        btnPersonel.setBorder(new EmptyBorder(10, 20, 10, 20));
        btnPersonel.setFocusPainted(false);
        btnPersonel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPersonel.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btnPersonel.setBackground(Theme.ACCENT_AMBER); }
            @Override public void mouseExited(MouseEvent e)  { btnPersonel.setBackground(Theme.ACCENT_SAFETY); }
        });
        btnPersonel.addActionListener(e -> {
            if (System.currentTimeMillis() - lastPopupHideMs < 250) return;
            showPersonelPopup(btnPersonel);
        });

        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnWrap.setOpaque(false);
        btnWrap.add(btnPersonel);

        rightCol.add(statusCol, BorderLayout.CENTER);
        rightCol.add(btnWrap, BorderLayout.EAST);
        header.add(rightCol, BorderLayout.EAST);
        return header;
    }

    private void showPersonelPopup(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_SUBTLE),
                new EmptyBorder(6, 0, 6, 0)
        ));
        popup.setBackground(Color.WHITE);
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                lastPopupHideMs = System.currentTimeMillis();
            }
            public void popupMenuCanceled(PopupMenuEvent e) {}
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
        });

        JLabel header = new JLabel("  AKTİF PERSONEL SEÇ");
        header.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 10f));
        header.setForeground(Theme.TEXT_MUTED);
        header.setBorder(new EmptyBorder(4, 12, 8, 12));
        popup.add(header);

        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER_SUBTLE);
        popup.add(sep);

        for (int i = 1; i < PERSONEL_LISTESI.length; i++) {
            final Personel p = PERSONEL_LISTESI[i];
            JMenuItem item = new JMenuItem(p.adSoyad) {
                @Override protected void paintComponent(Graphics g) {
                    if (getModel().isArmed() || getModel().isRollover()) {
                        g.setColor(new Color(0xEF, 0xF6, 0xFF));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    } else {
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                    super.paintComponent(g);
                }
            };
            item.setFont(Theme.FONT_BODY.deriveFont(13f));
            item.setForeground(Theme.TEXT_PRIMARY);
            item.setBorder(new EmptyBorder(9, 16, 9, 48));
            item.setOpaque(false);
            item.setBackground(Color.WHITE);
            String kkdOzet = String.join(", ", p.kayitliKKD);
            item.setToolTipText("KKD: " + kkdOzet);

            item.addActionListener(ev -> {
                personelSecildi(p);
                anchor.setText("Personeller  ·  " + p.adSoyad);
            });
            popup.add(item);
        }

        popup.show(anchor, 0, anchor.getHeight() + 4);
    }

    private JPanel buildCameraSection() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(Theme.BG_CAMERA_SURROUND);
        outer.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel camHeader = new JPanel(new BorderLayout());
        camHeader.setOpaque(false);
        camHeader.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel camTitle = new JLabel("Canlı Kamera Görüntüsü");
        camTitle.setFont(Theme.FONT_BODY_BOLD.deriveFont(Font.BOLD, 14f));
        camTitle.setForeground(Theme.TEXT_PRIMARY);
        JPanel livePill = createPillLabel("CANLI", Theme.ACCENT_RED, Color.WHITE, true);
        camHeader.add(camTitle, BorderLayout.WEST);
        camHeader.add(livePill, BorderLayout.EAST);

        JPanel frame = new JPanel(new BorderLayout());
        frame.setBackground(Theme.BG_CAMERA_FRAME);
        frame.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT_NAVY_DARK, 2),
                new EmptyBorder(4, 4, 4, 4)
        ));

        imageLabel = new JLabel("Sunucu Bağlantısı Bekleniyor...", SwingConstants.CENTER);
        imageLabel.setFont(Theme.FONT_BODY_BOLD.deriveFont(Font.PLAIN, 18f));
        imageLabel.setForeground(new Color(0x94, 0xA3, 0xB8));
        imageLabel.setBackground(Theme.BG_CAMERA_FRAME);
        imageLabel.setOpaque(true);
        frame.add(imageLabel, BorderLayout.CENTER);

        outer.add(camHeader, BorderLayout.NORTH);
        outer.add(frame, BorderLayout.CENTER);
        outer.add(buildCameraBottomBar(), BorderLayout.SOUTH);
        return outer;
    }

    private void initHistoryToggleButton() {
        btnToggleHistory = new JButton("Geçmiş İhlaller");
        btnToggleHistory.setPreferredSize(new Dimension(190, 42));
        btnToggleHistory.setMinimumSize(new Dimension(190, 42));
        stylePremiumButton(btnToggleHistory);
        btnToggleHistory.addActionListener(e -> toggleAlertPanel());
    }

    // ─── Canlı toast + geçmiş ihlaller butonu ───────────────────────────────

    private JPanel buildCameraBottomBar() {
        liveToastBanner = new LiveToastBanner();

        toastRowWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        toastRowWrap.setOpaque(false);
        toastRowWrap.setVisible(false);
        toastRowWrap.add(liveToastBanner);

        JPanel btnRow = new JPanel(new BorderLayout());
        btnRow.setOpaque(false);
        btnRow.setPreferredSize(new Dimension(0, 46));
        btnRow.add(btnToggleHistory, BorderLayout.EAST);

        JPanel bottomBar = new JPanel();
        bottomBar.setLayout(new BoxLayout(bottomBar, BoxLayout.Y_AXIS));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(14, 0, 0, 0));

        toastRowWrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        toastRowWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        bottomBar.add(toastRowWrap);
        bottomBar.add(Box.createVerticalStrut(8));
        bottomBar.add(btnRow);
        return bottomBar;
    }

    private JPanel buildBottomActionBar() {
        JLabel footerNote = new JLabel("ISG Bitirme Projesi  ·  YOLOv8 KKD Tespit Motoru");
        footerNote.setFont(Theme.FONT_CAPTION.deriveFont(11f));
        footerNote.setForeground(Theme.TEXT_MUTED);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_SIDEBAR);
        bar.setPreferredSize(new Dimension(0, 44));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SUBTLE),
                new EmptyBorder(10, 24, 10, 24)
        ));
        bar.add(footerNote, BorderLayout.WEST);
        return bar;
    }

    private void toggleAlertPanel() {
        if (alertLogPanel.isVisible()) {
            closeAlertPanel();
        } else {
            openAlertPanel();
        }
    }

    private void openAlertPanel() {
        alertLogPanel.setVisible(true);
        btnToggleHistory.setText("Paneli Kapat");
        revalidate();
        repaint();
    }

    private void closeAlertPanel() {
        alertLogPanel.setVisible(false);
        btnToggleHistory.setText("Geçmiş İhlaller");
        revalidate();
        repaint();
    }

    // ─── Geçmiş ihlaller paneli (tek sütun) ────────────────────────────────

    private JPanel buildCommandCenterPanel() {
        JPanel root = new DarkPanel(new BorderLayout(0, 0));
        root.setPreferredSize(new Dimension(920, 0));
        root.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER_SUBTLE));

        root.add(buildCommandHeader(), BorderLayout.NORTH);
        root.add(buildViolationListPanel(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildCommandHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, Theme.ACCENT_SAFETY),
                new EmptyBorder(16, 20, 16, 20)
        ));

        JPanel textCol = new JPanel(new GridLayout(2, 1, 0, 4));
        textCol.setOpaque(false);
        JLabel mainTitle = new JLabel("Geçmiş İhlaller");
        mainTitle.setFont(Theme.FONT_HEADING.deriveFont(Font.BOLD, 18f));
        mainTitle.setForeground(Color.WHITE);
        JLabel sub = new JLabel("KKD Denetim Kayıtları  ·  Kamera Kanıtlı İhlal Listesi");
        sub.setFont(Theme.FONT_CAPTION.deriveFont(11f));
        sub.setForeground(Theme.TEXT_ON_HEADER);
        textCol.add(mainTitle);
        textCol.add(sub);
        header.add(textCol, BorderLayout.CENTER);

        JButton btnClosePanel = new JButton("Paneli Kapat");
        btnClosePanel.setPreferredSize(new Dimension(130, 36));
        styleHeaderCloseButton(btnClosePanel);
        btnClosePanel.addActionListener(e -> closeAlertPanel());
        header.add(btnClosePanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildViolationListPanel() {
        JPanel col = new JPanel(new BorderLayout(0, 0));
        col.setBackground(Theme.BG_ROOT);
        col.setBorder(new EmptyBorder(0, 12, 8, 12));

        JPanel tableHeader = buildTableHeader();
        col.add(tableHeader, BorderLayout.NORTH);

        violationFeedPanel = new JPanel();
        violationFeedPanel.setLayout(new BoxLayout(violationFeedPanel, BoxLayout.Y_AXIS));
        violationFeedPanel.setBackground(Theme.BG_ROOT);
        violationFeedPanel.setBorder(new EmptyBorder(6, 0, 6, 0));
        seedOrnekIhlalKayitlari();

        JScrollPane feedScroll = new JScrollPane(violationFeedPanel);
        feedScroll.setBorder(BorderFactory.createEmptyBorder());
        feedScroll.getVerticalScrollBar().setUnitIncrement(16);
        feedScroll.setBackground(Theme.BG_ROOT);
        feedScroll.getViewport().setBackground(Theme.BG_ROOT);
        col.add(feedScroll, BorderLayout.CENTER);
        return col;
    }

    private JPanel buildTableHeader() {
        JPanel hdr = new JPanel(new GridLayout(1, 6, 8, 0));
        hdr.setBackground(Theme.BG_TABLE_HDR);
        hdr.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SUBTLE),
                new EmptyBorder(10, 14, 10, 14)
        ));
        hdr.add(tableHeaderCell("İhlal Kanıtı", SwingConstants.LEFT));
        hdr.add(tableHeaderCell("Personel ID", SwingConstants.LEFT));
        hdr.add(tableHeaderCell("Bölge", SwingConstants.LEFT));
        hdr.add(tableHeaderCell("Zaman", SwingConstants.LEFT));
        hdr.add(tableHeaderCell("Doğruluk", SwingConstants.CENTER));
        hdr.add(tableHeaderCell("İhlal Türü", SwingConstants.RIGHT));
        return hdr;
    }

    private static JLabel tableHeaderCell(String text, int align) {
        JLabel lbl = new JLabel(text, align);
        lbl.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 10f));
        lbl.setForeground(Theme.TEXT_HEADER);
        return lbl;
    }

    private void seedOrnekIhlalKayitlari() {
        kayitSayisi = 0;
        ekleKayit(IhlalKaydi.ornekBaret());
        ekleKayit(IhlalKaydi.ornekYelek());
        ekleKayit(IhlalKaydi.ornekMaske());
        ekleKayit(IhlalKaydi.ornekElbise());
    }

    private void ekleKayit(IhlalKaydi kayit) {
        violationFeedPanel.add(Box.createVerticalStrut(4));
        ViolationRow row = new ViolationRow(kayit);
        violationFeedPanel.add(row);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        kayitSayisi++;
    }

    // ─── Personel verisi ─────────────────────────────────────────────────────

    private static final class Personel {
        final String id;
        final String adSoyad;
        final List<String> kayitliKKD;

        Personel(String id, String adSoyad, String... kkd) {
            this.id = id;
            this.adSoyad = adSoyad;
            this.kayitliKKD = Arrays.asList(kkd);
        }

        @Override
        public String toString() { return adSoyad; }
    }

    private static final Personel[] PERSONEL_LISTESI = {
        new Personel("p0", "-- Personel Seçiniz --"),
        new Personel("p1", "Ahmet Yılmaz",    "Baret", "Yelek"),
        new Personel("p2", "Mehmet Demir",    "Baret"),
        new Personel("p3", "Ayşe Kaya",       "Yelek", "Maske"),
        new Personel("p4", "Mustafa Öztürk",  "Baret", "Yelek", "Maske", "Elbise"),
        new Personel("p5", "Fatma Çelik",     "Maske"),
        new Personel("p6", "Ömer Şahin",      "Yelek"),
    };

    private void personelSecildi(Personel p) {
        if (p == null || p.id.equals("p0")) {
            return;
        }
        List<String> kkd = p.kayitliKKD;
        cbHelmet .setSelected(kkd.contains("Baret"));
        cbVest   .setSelected(kkd.contains("Yelek"));
        cbMask   .setSelected(kkd.contains("Maske"));
        cbSuit   .setSelected(kkd.contains("Elbise"));
        cbBelt   .setSelected(kkd.contains("Kemer"));
        cbGlasses.setSelected(kkd.contains("Gözlük"));
        cbGloves .setSelected(kkd.contains("Eldiven"));
        cbWelding.setSelected(kkd.contains("Kaynak"));
        cbEar    .setSelected(kkd.contains("Kulak"));
        cbToolbox.setSelected(kkd.contains("Çanta"));
        updateRulesString();
    }

    // ─── Kontrol paneli (sol) ────────────────────────────────────────────────

    private void updateRulesString() {
        currentJsonRules = buildRulesJson();
    }

    private JPanel buildControlPanel() {
        cbMask = new JCheckBox("Maske Kontrolü");
        cbHelmet = new JCheckBox("Baret Kontrolü");
        cbBelt = new JCheckBox("Emniyet Kemeri Kontrolü");
        cbVest = new JCheckBox("Yelek Kontrolü");
        cbGlasses = new JCheckBox("Gözlük Kontrolü");
        cbSuit = new JCheckBox("Kimyasal Koruyucu Giysi");
        cbGloves = new JCheckBox("Koruyucu İş Eldiveni");
        cbToolbox = new JCheckBox("Alet Çantası Kontrolü");
        cbWelding = new JCheckBox("Kaynakçı Maskesi");
        cbEar = new JCheckBox("Kulak Koruyucu / Kulaklık");

        for (JCheckBox cb : new JCheckBox[]{
                cbMask, cbHelmet, cbBelt, cbVest, cbGlasses,
                cbSuit, cbGloves, cbToolbox, cbWelding, cbEar
        }) {
            styleCheckBox(cb);
            cb.addItemListener(e -> updateRulesString());
        }

        JCheckBox cbVestLogistics = new JCheckBox("Yelek Kontrolü");
        styleCheckBox(cbVestLogistics);
        cbVestLogistics.setModel(cbVest.getModel());
        cbVestLogistics.addItemListener(e -> updateRulesString());

        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setPreferredSize(new Dimension(340, 0));
        sidebar.setBackground(Theme.BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_SUBTLE));

        JPanel scrollContent = new JPanel(new GridBagLayout());
        scrollContent.setBackground(Theme.BG_SIDEBAR);
        scrollContent.setBorder(new EmptyBorder(20, 16, 16, 16));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 12, 0);

        JLabel lblSub = new JLabel("Sektörel denetim kurallarını seçin");
        lblSub.setFont(Theme.FONT_CAPTION.deriveFont(12f));
        lblSub.setForeground(Theme.TEXT_MUTED);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 10, 0);
        scrollContent.add(lblSub, c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 12, 0);
        scrollContent.add(createSectorCard("Genel & Şantiye Sahası", Theme.SECTOR_GENERAL,
                cbHelmet, cbVest, cbBelt), c);
        c.gridy = 2;
        scrollContent.add(createSectorCard("Ağır Sanayi & Kaynakhane", Theme.SECTOR_INDUSTRY,
                cbWelding, cbEar, cbGloves), c);
        c.gridy = 3;
        scrollContent.add(createSectorCard("Kimya & Laboratuvar", Theme.SECTOR_CHEMICAL,
                cbSuit, cbMask, cbGlasses), c);
        c.gridy = 4;
        c.insets = new Insets(0, 0, 0, 0);
        scrollContent.add(createSectorCard("Lojistik & Depolama", Theme.SECTOR_LOGISTICS,
                cbToolbox, cbVestLogistics), c);
        c.gridy = 5;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        scrollContent.add(Box.createVerticalGlue(), c);

        JScrollPane scroll = new JScrollPane(scrollContent);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_SIDEBAR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        sidebar.add(scroll, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel createSectorCard(String title, Color accent, JCheckBox... checkBoxes) {
        Objects.requireNonNull(title, "title");
        JPanel card = new JPanel(new GridLayout(0, 1, 0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER_SUBTLE),
                        new EmptyBorder(12, 14, 12, 14)
                )
        ));

        JCheckBox masterCb = new JCheckBox(title.toUpperCase(Locale.forLanguageTag("tr")));
        masterCb.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 10f));
        masterCb.setForeground(accent.darker());
        masterCb.setBackground(Color.WHITE);
        masterCb.setFocusPainted(false);
        masterCb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(masterCb);

        boolean[] updating = {false};

        masterCb.addActionListener(e -> {
            updating[0] = true;
            boolean sel = masterCb.isSelected();
            for (JCheckBox cb : checkBoxes) cb.setSelected(sel);
            updating[0] = false;
            updateRulesString();
        });

        ItemListener syncMaster = e -> {
            if (updating[0]) return;
            long checked = Arrays.stream(checkBoxes).filter(JCheckBox::isSelected).count();
            masterCb.setSelected(checked == checkBoxes.length);
        };

        for (JCheckBox cb : checkBoxes) {
            cb.addItemListener(syncMaster);
            card.add(cb);
        }
        return card;
    }

    private String buildRulesJson() {
        return String.format(
                Locale.ROOT,
                "{" +
                        "\"check_mask\": %b, " +
                        "\"check_helmet\": %b, " +
                        "\"check_vest\": %b, " +
                        "\"check_glasses\": %b, " +
                        "\"check_belt\": %b, " +
                        "\"check_suit\": %b, " +
                        "\"check_gloves\": %b, " +
                        "\"check_toolbox\": %b, " +
                        "\"check_welding\": %b, " +
                        "\"check_ear\": %b" +
                        "}",
                cbMask.isSelected(),
                cbHelmet.isSelected(),
                cbVest.isSelected(),
                cbGlasses.isSelected(),
                cbBelt.isSelected(),
                cbSuit.isSelected(),
                cbGloves.isSelected(),
                cbToolbox.isSelected(),
                cbWelding.isSelected(),
                cbEar.isSelected()
        );
    }

    // ─── Soket & ihlal işleme ──────────────────────────────────────────────

    private String readViolationPayload(DataInputStream dataIn) throws Exception {
        int violLen = dataIn.readInt();
        if (violLen <= 0) {
            return "";
        }
        byte[] violBytes = new byte[violLen];
        dataIn.readFully(violBytes);
        return new String(violBytes, StandardCharsets.UTF_8);
    }

    private void processViolationAlerts(String violationPayload) {
        if (violationPayload == null || violationPayload.isEmpty()) {
            return;
        }
        for (String line : violationPayload.split("\n")) {
            String violation = line.trim();
            if (violation.isEmpty()) {
                continue;
            }
            if (isRuleActiveForViolation(violation)) {
                appendAlertLog(violation);
                showLiveToast(violation);
            }
        }
    }

    private boolean isRuleActiveForViolation(String violation) {
        String v = violation.toLowerCase(Locale.forLanguageTag("tr"));

        if (v.contains("kaynak maskesi") || v.contains("kaynak")) {
            return cbWelding.isSelected();
        }
        if (v.contains("kulak")) {
            return cbEar.isSelected();
        }
        if (v.contains("maske")) {
            return cbMask.isSelected();
        }
        if (v.contains("baret")) {
            return cbHelmet.isSelected();
        }
        if (v.contains("yelek")) {
            return cbVest.isSelected();
        }
        if (v.contains("gözlük") || v.contains("gozluk")) {
            return cbGlasses.isSelected();
        }
        if (v.contains("kemer")) {
            return cbBelt.isSelected();
        }
        if (v.contains("elbise")) {
            return cbSuit.isSelected();
        }
        if (v.contains("eldiven")) {
            return cbGloves.isSelected();
        }
        if (v.contains("çantası") || v.contains("cantasi")) {
            return cbToolbox.isSelected();
        }
        return false;
    }

    private void showLiveToast(String violation) {
        liveToastBanner.showViolation(violation);
        toastRowWrap.setVisible(true);
        toastRowWrap.revalidate();
        toastRowWrap.repaint();

        if (toastHideTimer != null) {
            toastHideTimer.stop();
        }
        toastHideTimer = new Timer(TOAST_VISIBLE_MS, e -> {
            liveToastBanner.hideBanner();
            toastRowWrap.setVisible(false);
            toastRowWrap.getParent().revalidate();
            toastRowWrap.getParent().repaint();
            ((Timer) e.getSource()).stop();
        });
        toastHideTimer.setRepeats(false);
        toastHideTimer.start();
    }

    private void appendAlertLog(String violation) {
        long now = System.currentTimeMillis();
        Long last = lastViolationLogMs.get(violation);
        if (last != null && (now - last) < VIOLATION_LOG_COOLDOWN_MS) {
            return;
        }
        lastViolationLogMs.put(violation, now);

        IhlalKaydi kayit = IhlalKaydi.canlidan(violation, LocalDateTime.now().format(FULL_TS_FMT));
        ViolationRow row = new ViolationRow(kayit);
        violationFeedPanel.add(row, 0);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        kayitSayisi++;
        while (violationFeedPanel.getComponentCount() > MAX_ALERT_LOGS) {
            violationFeedPanel.remove(violationFeedPanel.getComponentCount() - 1);
        }
        violationFeedPanel.revalidate();
        violationFeedPanel.repaint();
    }

    private void connectToPythonServer() {
        try {
            Thread.sleep(1000);
            Socket socket = new Socket(HOST, PORT);

            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
            DataInputStream dataIn = new DataInputStream(socket.getInputStream());

            while (true) {
                byte[] jsonBytes = currentJsonRules.getBytes(StandardCharsets.UTF_8);
                dataOut.writeInt(jsonBytes.length);
                dataOut.write(jsonBytes);
                dataOut.flush();

                int length = dataIn.readInt();
                if (length > 0) {
                    byte[] message = new byte[length];
                    dataIn.readFully(message, 0, message.length);

                    String violationPayload = readViolationPayload(dataIn);

                    ImageIcon imageRaw = new ImageIcon(message);
                    Image imgScaled = imageRaw.getImage().getScaledInstance(
                            imageLabel.getWidth(), imageLabel.getHeight(), Image.SCALE_SMOOTH);
                    ImageIcon finalImage = new ImageIcon(imgScaled);

                    SwingUtilities.invokeLater(() -> {
                        processViolationAlerts(violationPayload);
                        imageLabel.setText(null);
                        imageLabel.setIcon(finalImage);
                    });
                }
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> imageLabel.setText("Bağlantı Koptu!"));
            e.printStackTrace();
        }
    }

    // ─── UI yardımcıları ─────────────────────────────────────────────────────

    private static void stylePremiumButton(JButton btn) {
        btn.setUI(new BasicButtonUI());
        btn.setFont(Theme.FONT_BODY_BOLD.deriveFont(13f));
        btn.setForeground(Color.WHITE);
        btn.setBackground(Theme.BG_HEADER);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT_NAVY_DARK),
                new EmptyBorder(10, 22, 10, 22)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(Theme.ACCENT_NAVY_LIGHT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(Theme.BG_HEADER);
            }
        });
    }

    private static void styleHeaderCloseButton(JButton btn) {
        btn.setUI(new BasicButtonUI());
        btn.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 12f));
        btn.setForeground(Color.WHITE);
        btn.setBackground(Theme.ACCENT_NAVY_LIGHT);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBF, 0xDB, 0xFE)),
                new EmptyBorder(8, 16, 8, 16)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(Theme.ACCENT_SAFETY);
                btn.setForeground(Theme.BG_HEADER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(Theme.ACCENT_NAVY_LIGHT);
                btn.setForeground(Color.WHITE);
            }
        });
    }

    private static void styleCheckBox(JCheckBox cb) {
        cb.setFont(Theme.FONT_BODY.deriveFont(14f));
        cb.setForeground(Theme.TEXT_PRIMARY);
        cb.setBackground(Color.WHITE);
        cb.setFocusPainted(false);
        cb.setOpaque(true);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static JPanel createPillLabel(String text, Color bg, Color fg, boolean bold) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(Theme.FONT_CAPTION.deriveFont(bold ? Font.BOLD : Font.PLAIN, 10f));
        lbl.setForeground(fg);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setBorder(new EmptyBorder(4, 10, 4, 10));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(lbl);
        return wrap;
    }

    // ─── Tema & bileşenler ───────────────────────────────────────────────────

    private static final class Theme {
        static final Color BG_APP = new Color(0xF1, 0xF5, 0xF9);
        static final Color BG_ROOT = new Color(0xED, 0xF1, 0xF5);
        static final Color BG_SIDEBAR = Color.WHITE;
        static final Color BG_HEADER = new Color(0x0F, 0x2B, 0x4A);
        static final Color BG_TABLE_HDR = new Color(0xE2, 0xE8, 0xF0);
        static final Color BG_ROW = Color.WHITE;
        static final Color BG_HOVER = new Color(0xF8, 0xFA, 0xFC);
        static final Color BG_ELEVATED = new Color(0xE2, 0xE8, 0xF0);
        static final Color BG_CAMERA_SURROUND = new Color(0xE8, 0xED, 0xF3);
        static final Color BG_CAMERA_FRAME = new Color(0x0F, 0x17, 0x2A);
        static final Color BORDER_SUBTLE = new Color(0xCB, 0xD5, 0xE1);
        static final Color TEXT_PRIMARY = new Color(0x0F, 0x17, 0x2A);
        static final Color TEXT_HEADER = new Color(0x47, 0x55, 0x69);
        static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);
        static final Color TEXT_ON_HEADER = new Color(0xBF, 0xDB, 0xFE);
        static final Color ACCENT_NAVY_DARK = new Color(0x0B, 0x22, 0x3C);
        static final Color ACCENT_NAVY_LIGHT = new Color(0x1E, 0x4D, 0x7B);
        static final Color ACCENT_SAFETY = new Color(0xF5, 0x9E, 0x0B);
        static final Color ACCENT_GREEN = new Color(0x05, 0x96, 0x69);
        static final Color ACCENT_GREEN_SOFT = new Color(0xEC, 0xFD, 0xF5);
        static final Color ACCENT_RED = new Color(0xDC, 0x26, 0x26);
        static final Color ACCENT_RED_SOFT = new Color(0xFE, 0xF2, 0xF2);
        static final Color ACCENT_AMBER = new Color(0xD9, 0x77, 0x06);
        static final Color SECTOR_GENERAL = new Color(0x25, 0x63, 0xEB);
        static final Color SECTOR_INDUSTRY = new Color(0xEA, 0x58, 0x0C);
        static final Color SECTOR_CHEMICAL = new Color(0x05, 0x96, 0x69);
        static final Color SECTOR_LOGISTICS = new Color(0x7C, 0x3A, 0xED);
        static final Font FONT_DISPLAY = new Font("Segoe UI", Font.PLAIN, 22);
        static final Font FONT_HEADING = new Font("Segoe UI", Font.PLAIN, 20);
        static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
        static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
        static final Font FONT_CAPTION = new Font("Segoe UI", Font.PLAIN, 11);
    }

    /** Komuta merkezi kök paneli – cam efekti yok, düz koyu zemin */
    private static class DarkPanel extends JPanel {
        DarkPanel(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(Theme.BG_ROOT);
        }
    }

    /** Tek satırlık ihlal kaydı */
    private static class ViolationRow extends JPanel {
        ViolationRow(IhlalKaydi kayit) {
            super(new GridLayout(1, 6, 10, 0));
            setBackground(Theme.BG_ROW);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(0, 0, 6, 0),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Theme.BORDER_SUBTLE),
                            BorderFactory.createCompoundBorder(
                                    BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.ACCENT_RED),
                                    new EmptyBorder(10, 12, 10, 12)
                            )
                    )
            ));
            setPreferredSize(new Dimension(0, 84));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

            KanitOnizleme kanit = new KanitOnizleme(kayit.kanitTipi, kayit.tespitEtiketi);
            kanit.setPreferredSize(new Dimension(118, 62));
            add(kanit);

            add(metrikHucre("Personel ID", kayit.personelId, false));
            add(metrikHucre("Bölge", kayit.bolge, false));
            add(metrikHucre("Zaman", kayit.zaman, false));
            add(metrikHucre("Doğruluk", kayit.dogruluk, true));

            JPanel aksiyon = new JPanel();
            aksiyon.setLayout(new BoxLayout(aksiyon, BoxLayout.Y_AXIS));
            aksiyon.setOpaque(false);
            JLabel ihlalLbl = new JLabel(kayit.ihlalBaslik);
            ihlalLbl.setFont(Theme.FONT_BODY_BOLD.deriveFont(Font.BOLD, 12f));
            ihlalLbl.setForeground(Theme.ACCENT_RED);
            ihlalLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JLabel badge = new JLabel("KRİTİK İHLAL", SwingConstants.CENTER);
            badge.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 9f));
            badge.setForeground(Color.WHITE);
            badge.setBackground(Theme.ACCENT_RED);
            badge.setOpaque(true);
            badge.setAlignmentX(Component.RIGHT_ALIGNMENT);
            badge.setBorder(new EmptyBorder(5, 12, 5, 12));
            aksiyon.add(ihlalLbl);
            aksiyon.add(Box.createVerticalStrut(6));
            aksiyon.add(badge);
            add(aksiyon);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(Theme.BG_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(Theme.BG_ROW);
                }
            });
        }

        private static JPanel metrikHucre(String baslik, String deger, boolean pill) {
            JPanel p = new JPanel(new BorderLayout(0, 4));
            p.setOpaque(false);
            JLabel lbl = new JLabel(baslik);
            lbl.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 9f));
            lbl.setForeground(Theme.TEXT_MUTED);
            JLabel val = new JLabel(deger);
            val.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 11f));
            if (pill && deger.contains("%")) {
                val.setForeground(Theme.ACCENT_GREEN);
                val.setOpaque(true);
                val.setBackground(Theme.ACCENT_GREEN_SOFT);
                val.setBorder(new EmptyBorder(3, 8, 3, 8));
            } else {
                val.setForeground(Theme.TEXT_PRIMARY);
            }
            p.add(lbl, BorderLayout.NORTH);
            p.add(val, BorderLayout.CENTER);
            return p;
        }
    }

    private static class IhlalKaydi {
        final KanitTipi kanitTipi;
        final String tespitEtiketi;
        final String personelId;
        final String bolge;
        final String zaman;
        final String dogruluk;
        final String ihlalBaslik;

        IhlalKaydi(KanitTipi kanitTipi, String tespitEtiketi, String personelId, String bolge,
                   String zaman, String dogruluk, String ihlalBaslik) {
            this.kanitTipi = kanitTipi;
            this.tespitEtiketi = tespitEtiketi;
            this.personelId = personelId;
            this.bolge = bolge;
            this.zaman = zaman;
            this.dogruluk = dogruluk;
            this.ihlalBaslik = ihlalBaslik;
        }

        static IhlalKaydi ornekBaret() {
            return new IhlalKaydi(KanitTipi.BARET, "Baret Tespit Edilmedi", "OPERATÖR 100+",
                    "Fabrika 1 - Bölüm C", "2026-06-07 10:32:15", "98%", "KASK EKSİK");
        }

        static IhlalKaydi ornekYelek() {
            return new IhlalKaydi(KanitTipi.YELEK, "Yelek Tespit Edilmedi", "OPERATÖR 100+",
                    "Depo 2 - Giriş", "2026-06-07 10:28:40", "95%", "YELEK EKSİK");
        }

        static IhlalKaydi ornekMaske() {
            return new IhlalKaydi(KanitTipi.MASKE, "Maske Tespit Edilmedi", "OPERATÖR 100+",
                    "Kimya Laboratuvarı", "2026-06-07 10:15:22", "97%", "MASKE EKSİK");
        }

        static IhlalKaydi ornekElbise() {
            return new IhlalKaydi(KanitTipi.ELBISE, "Koruyucu Giysi Tespit Edilmedi", "OPERATÖR 100+",
                    "Kimya Laboratuvarı", "2026-06-07 09:55:12", "94%", "KORUYUCU GİYSİ EKSİK");
        }

        static IhlalKaydi canlidan(String violation, String zaman) {
            String v = violation.toLowerCase(Locale.forLanguageTag("tr"));
            if (v.contains("kaynak maskesi") || (v.contains("kaynak") && !v.contains("maske yok"))) {
                return new IhlalKaydi(KanitTipi.KAYNAK, "Kaynak Maskesi Tespit Edilmedi", "OPERATÖR 100+",
                        "Kaynakhane", zaman, "96%", "KAYNAK MASKESİ EKSİK");
            }
            if (v.contains("kulak")) {
                return new IhlalKaydi(KanitTipi.KULAK, "Kulak Koruyucu Tespit Edilmedi", "OPERATÖR 100+",
                        "Fabrika 1", zaman, "93%", "KULAK KORUYUCU EKSİK");
            }
            if (v.contains("maske")) {
                return ornekMaske().withZaman(zaman);
            }
            if (v.contains("baret")) {
                return ornekBaret().withZaman(zaman);
            }
            if (v.contains("yelek")) {
                return ornekYelek().withZaman(zaman);
            }
            if (v.contains("gözlük") || v.contains("gozluk")) {
                return new IhlalKaydi(KanitTipi.GOZLUK, "Gözlük Tespit Edilmedi", "OPERATÖR 100+",
                        "Laboratuvar", zaman, "92%", "GÖZLÜK EKSİK");
            }
            if (v.contains("kemer")) {
                return new IhlalKaydi(KanitTipi.KEMER, "Emniyet Kemeri Tespit Edilmedi", "OPERATÖR 100+",
                        "Şantiye A1", zaman, "91%", "KEMER EKSİK");
            }
            if (v.contains("elbise")) {
                return ornekElbise().withZaman(zaman);
            }
            if (v.contains("eldiven")) {
                return new IhlalKaydi(KanitTipi.ELDIVEN, "Eldiven Tespit Edilmedi", "OPERATÖR 100+",
                        "Kaynakhane 2", zaman, "90%", "ELDİVEN EKSİK");
            }
            if (v.contains("çantası") || v.contains("cantasi")) {
                return new IhlalKaydi(KanitTipi.CANTA, "Alet Çantası Tespit Edilmedi", "OPERATÖR 100+",
                        "Depo 1", zaman, "89%", "ALET ÇANTASI EKSİK");
            }
            return new IhlalKaydi(KanitTipi.BARET, "KKD Tespit Edilmedi", "OPERATÖR 100+",
                    "Genel Alan", zaman, "88%", "KKD İHLALİ");
        }

        IhlalKaydi withZaman(String yeniZaman) {
            return new IhlalKaydi(kanitTipi, tespitEtiketi, personelId, bolge, yeniZaman,
                    dogruluk, ihlalBaslik);
        }
    }

    private enum KanitTipi {
        BARET("baret.jpg", 0.5000, 0.3834, 0.0850, 0.1000),
        MASKE("maske.jpg", 0.4014, 0.3333, 0.0801, 0.1202),
        YELEK("yelek.jpg", 0.2909, 0.4988, 0.5000, 0.6995),
        ELBISE("elbise.png", 0.4900, 0.6450, 0.5600, 0.6900),
        GOZLUK("gozluk.jpg", 0.4450, 0.3792, 0.3300, 0.2417),
        KEMER("kemer.jpg", 0.5031, 0.4938, 0.4781, 0.7875),
        KAYNAK("kaynak.jpg", 0.5437, 0.3284, 0.4123, 0.5244),
        KULAK("kulak.jpg", 0.5076, 0.1250, 0.0506, 0.1853),
        ELDIVEN("eldiven.jpg", 0.5858, 0.5804, 0.4706, 0.6985),
        CANTA("canta.jpg", 0.5330, 0.6698, 0.5996, 0.3028);

        final String dosya;
        final double bboxCx;
        final double bboxCy;
        final double bboxW;
        final double bboxH;

        KanitTipi(String dosya, double bboxCx, double bboxCy, double bboxW, double bboxH) {
            this.dosya = dosya;
            this.bboxCx = bboxCx;
            this.bboxCy = bboxCy;
            this.bboxW = bboxW;
            this.bboxH = bboxH;
        }
    }

    /** Eğitim veri setinden alınan KKD referans görselleri */
    private static final class PpeGorseller {
        private static final Map<KanitTipi, BufferedImage> GORSELLER = new EnumMap<>(KanitTipi.class);

        static {
            for (KanitTipi tip : KanitTipi.values()) {
                GORSELLER.put(tip, yukle(tip.dosya));
            }
        }

        private static BufferedImage yukle(String dosyaAdi) {
            String[] tabanlar = {
                    "assets/kanit/",
                    "Apps/DesktopApp/assets/kanit/"
            };
            for (String taban : tabanlar) {
                File f = new File(taban + dosyaAdi);
                if (f.isFile()) {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            return img;
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            return null;
        }

        static BufferedImage get(KanitTipi tip) {
            BufferedImage img = GORSELLER.get(tip);
            if (img != null) {
                return img;
            }
            return GORSELLER.get(KanitTipi.BARET);
        }
    }

    /** KKD fotoğrafı + tespit kutusu ile kanıt önizlemesi */
    private static class KanitOnizleme extends JComponent {
        private final KanitTipi tip;
        private final String etiket;

        KanitOnizleme(KanitTipi tip, String etiket) {
            this.tip = tip;
            this.etiket = etiket;
            setOpaque(true);
            setBackground(new Color(0xE2, 0xE8, 0xF0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int lblH = 14;
            int imgH = Math.max(1, getHeight() - lblH);

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            BufferedImage gorsel = PpeGorseller.get(tip);
            if (gorsel != null && getWidth() > 0 && imgH > 0) {
                int[] yerlesim = hesaplaGorselYerlesimi(gorsel, getWidth(), imgH);
                int dx = yerlesim[0];
                int dy = yerlesim[1];
                int dw = yerlesim[2];
                int dh = yerlesim[3];
                g2.drawImage(gorsel, dx, dy, dw, dh, null);

                int bx = dx + (int) ((tip.bboxCx - tip.bboxW / 2.0) * dw);
                int by = dy + (int) ((tip.bboxCy - tip.bboxH / 2.0) * dh);
                int bw = Math.max(4, (int) (tip.bboxW * dw));
                int bh = Math.max(4, (int) (tip.bboxH * dh));
                g2.setColor(Theme.ACCENT_RED);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(bx, by, bw, bh);
            }

            g2.setColor(new Color(0x1E, 0x49, 0x76, 210));
            g2.fillRoundRect(0, imgH, getWidth(), lblH, 0, 0);
            g2.setFont(Theme.FONT_CAPTION.deriveFont(8f));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(etiket);
            g2.drawString(etiket, Math.max(2, (getWidth() - tw) / 2), getHeight() - 4);
            g2.dispose();
        }
    }

    /** Canlı ihlal bildirimi – kart tarzı toast */
    private static class LiveToastBanner extends JPanel {
        private final JLabel titleLbl;
        private final JLabel messageLbl;

        LiveToastBanner() {
            super(new BorderLayout(14, 0));
            setOpaque(false);
            setVisible(false);
            setPreferredSize(new Dimension(420, 68));
            setMinimumSize(new Dimension(360, 64));
            setMaximumSize(new Dimension(460, 72));
            setBorder(new EmptyBorder(14, 20, 14, 22));

            JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            iconWrap.setOpaque(false);
            iconWrap.setPreferredSize(new Dimension(44, 44));
            iconWrap.add(new WarningIcon());

            JPanel textCol = new JPanel(new GridLayout(2, 1, 0, 2));
            textCol.setOpaque(false);
            titleLbl = new JLabel("KRİTİK İHLAL");
            titleLbl.setFont(Theme.FONT_CAPTION.deriveFont(Font.BOLD, 10f));
            titleLbl.setForeground(Theme.ACCENT_RED);
            messageLbl = new JLabel(" ");
            messageLbl.setFont(Theme.FONT_BODY_BOLD.deriveFont(Font.BOLD, 15f));
            messageLbl.setForeground(Theme.TEXT_PRIMARY);
            textCol.add(titleLbl);
            textCol.add(messageLbl);

            add(iconWrap, BorderLayout.WEST);
            add(textCol, BorderLayout.CENTER);
        }

        void showViolation(String violation) {
            messageLbl.setText(violation);
            setVisible(true);
            revalidate();
            repaint();
        }

        void hideBanner() {
            setVisible(true);
            messageLbl.setText(" ");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 20;
            int pad = 2;

            g2.setColor(new Color(0, 0, 0, 28));
            g2.fillRoundRect(pad + 1, pad + 3, w - pad * 2 - 2, h - pad - 3, arc, arc);

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(pad, pad, w - pad * 2, h - pad * 2, arc, arc);

            g2.setColor(Theme.ACCENT_RED_SOFT);
            g2.fillRoundRect(pad + 4, pad + 4, w - pad * 2 - 8, h - pad * 2 - 8, arc - 4, arc - 4);

            g2.setColor(new Color(0xFC, 0xA5, 0xA5));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(pad, pad, w - pad * 2 - 1, h - pad * 2 - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class WarningIcon extends JComponent {
        WarningIcon() {
            setPreferredSize(new Dimension(36, 36));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 4;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            g2.setColor(Theme.ACCENT_RED);
            g2.fillOval(x, y, d, d);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.FONT_BODY_BOLD.deriveFont(Font.BOLD, 18f));
            FontMetrics fm = g2.getFontMetrics();
            String mark = "!";
            g2.drawString(mark, x + (d - fm.stringWidth(mark)) / 2, y + (d + fm.getAscent()) / 2 - 3);
            g2.dispose();
        }
    }

    /** Görseli kırpmadan (contain) hedef alana yerleştirir: [dx, dy, dw, dh] */
    private static int[] hesaplaGorselYerlesimi(BufferedImage gorsel, int alanW, int alanH) {
        double scale = Math.min((double) alanW / gorsel.getWidth(),
                (double) alanH / gorsel.getHeight());
        int dw = Math.max(1, (int) (gorsel.getWidth() * scale));
        int dh = Math.max(1, (int) (gorsel.getHeight() * scale));
        int dx = (alanW - dw) / 2;
        int dy = (alanH - dh) / 2;
        return new int[]{dx, dy, dw, dh};
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("ScrollBar.width", 10);
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(SafetyApp::new);
    }
}
