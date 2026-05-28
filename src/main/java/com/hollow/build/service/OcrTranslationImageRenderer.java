package com.hollow.build.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OCR 翻译对照图渲染器。
 * <p>
 * 纯绘图逻辑，不依赖 OCR / 翻译 / 存储，输入"原图 + 文本块（含原文与译文 + bbox）"，
 * 输出左右两栏对照 PNG：左栏为原图加检测框与 #N 角标，右栏为与原图等尺寸、同坐标系的译文层，
 * 译文块尽量贴近原文在图中的位置（"位置对齐"），仅对横向重叠的块做纵向避让。
 * <p>
 * 每个文本块按 #N 分配一种专属颜色，左栏检测框、右栏译文块与两侧角标同色，方便顺色对照。
 * 角标统一放在框内右下角、紧贴框线、半透明。
 * <p>
 * 右栏标题栏会显示统计元信息（OCR/翻译时长、块数、目标语言）；当面板宽度不足以单行容纳时，
 * 自动把元信息放到标题下方一条独立的副条带里，且左右两栏 header 高度一致以保持坐标对照。
 * 两栏 panel 高度按 max(原图高, 右栏内容高) 对齐，避免左栏底部出现"漂浮"缺边的视觉错位；
 * 原图四边内缩 {@code IMAGE_INSET} 像素，让面板外框完整露出来。
 */
@Service
public class OcrTranslationImageRenderer {

    /** 单个文本块：原文、译文与 OCR 多边形顶点（原图坐标系）。 */
    public record OcrTextItem(int index, String text, String translation, List<Point2D.Double> bbox) {
    }

    /** 渲染时显示在右栏标题栏的统计信息。传 null 表示不显示。 */
    public record RenderStats(long ocrMillis, long translateMillis) {
    }

    private static final int MAX_PANEL_WIDTH = 1100;
    private static final int MAX_PANEL_HEIGHT = 1500;
    private static final int TITLE_HEIGHT = 44;
    private static final int META_BAR_HEIGHT = 22;
    private static final int TITLE_PAD_X = 14;
    private static final int TITLE_META_GAP = 24;
    private static final int OUTER_PADDING = 20;
    private static final int PANEL_GAP = 18;
    /** 原图（含右栏译文区）与面板外框之间的内边距，让灰色边框完整可见。 */
    private static final int IMAGE_INSET = 1;

    private static final int LABEL_PAD_X = 8;
    private static final int LABEL_PAD_Y = 5;
    private static final int LABEL_GAP = 5;
    private static final int MIN_LABEL_WIDTH = 96;
    private static final int MIN_FONT_SIZE = 13;
    private static final int MAX_FONT_SIZE = 17;
    private static final int MAX_LABEL_LINES = 8;
    private static final int BADGE_ALPHA = 185;

    private static final Color CANVAS_BG = new Color(243, 245, 248);
    private static final Color PANEL_BORDER = new Color(224, 228, 235);
    private static final Color TITLE_BG = new Color(248, 250, 252);
    private static final Color ACCENT = new Color(37, 99, 235);
    private static final Color TEXT_COLOR = new Color(17, 24, 39);
    private static final Color IMAGE_BORDER = new Color(231, 236, 242);
    private static final Color META_TEXT = new Color(107, 114, 128);

    /** #N 专属配色（Tailwind 600 级，色相区分度高且都能配白字）。 */
    private static final Color[] PALETTE = {
            new Color(0x2563EB), // blue
            new Color(0xDC2626), // red
            new Color(0x16A34A), // green
            new Color(0xD97706), // amber
            new Color(0x7C3AED), // violet
            new Color(0x0891B2), // cyan
            new Color(0xDB2777), // pink
            new Color(0x65A30D), // lime
            new Color(0xEA580C), // orange
            new Color(0x4F46E5), // indigo
            new Color(0x0D9488), // teal
            new Color(0xC026D3)  // fuchsia
    };

    private final String fontFamily = resolveFontFamily();

    /**
     * 渲染对照图。
     *
     * @param sourceImage    原图（未缩放）
     * @param items          文本块列表（含译文与 bbox）
     * @param targetLanguage 目标语言，仅用于右栏元信息
     * @param stats          统计信息，null 则不显示元信息条
     * @return PNG 字节
     */
    public byte[] render(BufferedImage sourceImage, List<OcrTextItem> items, String targetLanguage,
                         RenderStats stats) throws Exception {
        double scale = Math.min(1.0, Math.min(
                MAX_PANEL_WIDTH / (double) sourceImage.getWidth(),
                MAX_PANEL_HEIGHT / (double) sourceImage.getHeight()
        ));
        BufferedImage image = scaleImage(sourceImage, scale);

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int panelWidth = imageWidth + IMAGE_INSET * 2;
        int outputWidth = OUTER_PADDING * 2 + panelWidth * 2 + PANEL_GAP;

        Font titleFont = new Font(fontFamily, Font.BOLD, 20);
        Font badgeFont = new Font(fontFamily, Font.BOLD, 11);
        Font metaFont = new Font(fontFamily, Font.PLAIN, 12);

        // 1. 测量元信息文字是否能单行容纳在标题栏右侧；放不下则启用副条带（标题下方一条 22px 灰字行）
        int metaBarHeight = 0;
        String metaText = null;
        if (stats != null) {
            metaText = formatMeta(stats, items.size(), targetLanguage);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D probeG = probe.createGraphics();
            try {
                int titleWidth = probeG.getFontMetrics(titleFont).stringWidth("译文对照");
                int metaWidth = probeG.getFontMetrics(metaFont).stringWidth(metaText);
                int needed = TITLE_PAD_X + titleWidth + TITLE_META_GAP + metaWidth + TITLE_PAD_X;
                if (needed > panelWidth) {
                    metaBarHeight = META_BAR_HEIGHT;
                }
            } finally {
                probeG.dispose();
            }
        }
        int headerHeight = TITLE_HEIGHT + metaBarHeight;

        int leftPanelX = OUTER_PADDING;
        int rightPanelX = OUTER_PADDING + panelWidth + PANEL_GAP;
        int panelY = OUTER_PADDING;
        int leftImageX = leftPanelX + IMAGE_INSET;
        int rightImageX = rightPanelX + IMAGE_INSET;
        int imageY = panelY + headerHeight + IMAGE_INSET;

        // 2. 算右栏译文层布局与实际内容高度
        List<TranslationLabel> labels;
        int rightInnerHeight;
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        try {
            applyQualityHints(metricsGraphics);
            labels = layoutTranslationLabels(metricsGraphics, items, rightImageX, imageY,
                    imageWidth, imageHeight, scale);
            int bottom = imageY + imageHeight;
            for (TranslationLabel label : labels) {
                bottom = Math.max(bottom, label.rect().y + label.rect().height + LABEL_GAP);
            }
            rightInnerHeight = bottom - imageY;
        } finally {
            metricsGraphics.dispose();
        }

        // 3. 两栏共用同一 inner 高度（取较大者），保证左右 panel 外框等高对齐
        int sharedInnerHeight = Math.max(imageHeight, rightInnerHeight);
        int panelHeight = headerHeight + IMAGE_INSET * 2 + sharedInnerHeight;
        int outputHeight = OUTER_PADDING * 2 + panelHeight;

        BufferedImage output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            applyQualityHints(g);
            g.setColor(CANVAS_BG);
            g.fillRect(0, 0, outputWidth, outputHeight);

            drawOriginalPanel(g, image, "原图 OCR 标注", leftPanelX, panelY, panelWidth, panelHeight,
                    headerHeight, leftImageX, imageY, items, scale, titleFont, badgeFont);
            drawTranslationPanel(g, rightPanelX, panelY, panelWidth, panelHeight, headerHeight,
                    rightImageX, imageY, imageWidth, rightInnerHeight, "译文对照",
                    labels, items.isEmpty(), titleFont, badgeFont);
            if (metaText != null) {
                drawTitleMeta(g, rightPanelX, panelY, panelWidth, metaBarHeight, metaText, metaFont);
            }
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(output, "png", baos);
        return baos.toByteArray();
    }

    // ------------------------------------------------------------------
    // 左栏：原图 + 检测框 + #N 角标
    // ------------------------------------------------------------------

    private void drawOriginalPanel(Graphics2D g, BufferedImage image, String title, int panelX, int panelY,
                                   int panelWidth, int panelHeight, int headerHeight,
                                   int imageX, int imageY, List<OcrTextItem> items, double scale,
                                   Font titleFont, Font badgeFont) {
        drawPanelChrome(g, panelX, panelY, panelWidth, panelHeight, headerHeight, title, titleFont);
        g.drawImage(image, imageX, imageY, null);

        if (items.isEmpty()) {
            drawEmptyHint(g, imageX, imageY, image.getWidth(), image.getHeight(), badgeFont);
            return;
        }

        g.setStroke(new BasicStroke(1.4f));
        for (OcrTextItem item : items) {
            drawBox(g, item, imageX, imageY, scale);
        }
        g.setFont(badgeFont);
        for (OcrTextItem item : items) {
            drawCornerBadge(g, item, imageX, imageY, image.getWidth(), image.getHeight(), scale, badgeFont);
        }
    }

    private void drawBox(Graphics2D g, OcrTextItem item, int imageX, int imageY, double scale) {
        int[] xs = new int[item.bbox().size()];
        int[] ys = new int[item.bbox().size()];
        for (int i = 0; i < item.bbox().size(); i++) {
            Point2D.Double point = item.bbox().get(i);
            xs[i] = imageX + (int) Math.round(point.x * scale);
            ys[i] = imageY + (int) Math.round(point.y * scale);
        }
        g.setColor(colorFor(item.index()));
        g.drawPolygon(xs, ys, xs.length);
    }

    /** 角标画在框内右下角、紧贴框线、半透明，颜色与该 #N 一致。 */
    private void drawCornerBadge(Graphics2D g, OcrTextItem item, int imageX, int imageY,
                                 int imageWidth, int imageHeight, double scale, Font font) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        String text = "#" + item.index();
        Box box = scaledBox(item, imageX, imageY, scale);
        int width = metrics.stringWidth(text) + 8;
        int height = metrics.getHeight();
        int x = Math.max(box.minX(), box.maxX() - width);
        int y = Math.max(box.minY(), box.maxY() - height);
        x = clamp(x, imageX, imageX + imageWidth - width);
        y = clamp(y, imageY, imageY + imageHeight - height);
        drawBadge(g, text, x, y, width, height, metrics, withAlpha(colorFor(item.index()), BADGE_ALPHA));
    }

    // ------------------------------------------------------------------
    // 右栏：等尺寸译文层，位置贴近原文
    // ------------------------------------------------------------------

    private void drawTranslationPanel(Graphics2D g, int panelX, int panelY, int panelWidth, int panelHeight,
                                      int headerHeight, int imageX, int imageY, int imageWidth, int innerHeight,
                                      String title, List<TranslationLabel> labels, boolean empty,
                                      Font titleFont, Font badgeFont) {
        drawPanelChrome(g, panelX, panelY, panelWidth, panelHeight, headerHeight, title, titleFont);

        // 译文区只画"实际译文高度"的内框（白底 + 浅灰边），下方多出的对齐区域留作面板白底
        g.setColor(Color.WHITE);
        g.fillRect(imageX, imageY, imageWidth, innerHeight);
        g.setColor(IMAGE_BORDER);
        g.drawRect(imageX, imageY, imageWidth - 1, innerHeight - 1);

        if (empty) {
            drawEmptyHint(g, imageX, imageY, imageWidth, innerHeight, badgeFont);
            return;
        }
        for (TranslationLabel label : labels) {
            drawTranslationLabel(g, label, badgeFont);
        }
    }

    /**
     * 绘制右栏元信息：OCR/翻译时长 · 文本块数 · 目标语言。
     * <p>
     * metaBarHeight == 0：标题栏右侧单行右对齐；
     * metaBarHeight > 0 ：副条带（标题下方 22px）右对齐。
     */
    private void drawTitleMeta(Graphics2D g, int panelX, int panelY, int panelWidth,
                               int metaBarHeight, String text, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = panelX + panelWidth - TITLE_PAD_X - textWidth;
        int barTop;
        int barHeight;
        if (metaBarHeight > 0) {
            barTop = panelY + TITLE_HEIGHT;
            barHeight = metaBarHeight;
        } else {
            barTop = panelY;
            barHeight = TITLE_HEIGHT;
        }
        int y = barTop + (barHeight + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(META_TEXT);
        g.drawString(text, x, y);
    }

    private String formatMeta(RenderStats stats, int itemCount, String targetLanguage) {
        return String.join("  ·  ",
                "OCR " + formatMillis(stats.ocrMillis()),
                "翻译 " + formatMillis(stats.translateMillis()),
                itemCount + " 块",
                StringUtils.defaultIfBlank(targetLanguage, "中文"));
    }

    private String formatMillis(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    private List<TranslationLabel> layoutTranslationLabels(Graphics2D g, List<OcrTextItem> items, int imageX, int imageY,
                                                           int imageWidth, int imageHeight, double scale) {
        if (items.isEmpty()) {
            return List.of();
        }

        List<LayoutCandidate> candidates = new ArrayList<>();
        for (OcrTextItem item : items) {
            Box box = scaledBox(item, imageX, imageY, scale);
            int fontSize = fontSizeForBox(box.height());
            Font textFont = new Font(fontFamily, Font.PLAIN, fontSize);
            g.setFont(textFont);
            FontMetrics textMetrics = g.getFontMetrics();

            int chrome = LABEL_PAD_X * 2;
            int x = clamp(box.minX(), imageX + 4, imageX + imageWidth - MIN_LABEL_WIDTH - 4);
            int maxWidth = imageX + imageWidth - 4 - x;

            // 块宽至少容纳原框宽度，并尽量让短译文单行放下（避免"功能特/性"这类无谓折行）。
            String translation = StringUtils.defaultString(item.translation(), item.text());
            int singleLineNeed = textMetrics.stringWidth(translation) + chrome;
            int width = clamp(Math.max(box.width(), singleLineNeed), MIN_LABEL_WIDTH, maxWidth);
            int textWidth = Math.max(24, width - chrome);
            List<String> lines = wrapText(translation, textMetrics, textWidth, MAX_LABEL_LINES);
            int textHeight = lines.size() * textMetrics.getHeight();
            int height = Math.max(box.height(), textHeight + LABEL_PAD_Y * 2);

            int desiredY = clamp(box.minY(), imageY + 4, Math.max(imageY + 4, imageY + imageHeight - height - 4));
            candidates.add(new LayoutCandidate(item, x, width, height, desiredY, fontSize, lines));
        }

        candidates.sort(Comparator.comparingInt(LayoutCandidate::desiredY)
                .thenComparingInt(LayoutCandidate::x));

        List<TranslationLabel> placed = new ArrayList<>();
        for (LayoutCandidate candidate : candidates) {
            int y = candidate.desiredY();
            while (true) {
                int nextY = y;
                for (TranslationLabel other : placed) {
                    Rectangle r = other.rect();
                    boolean xOverlap = candidate.x() < r.x + r.width && r.x < candidate.x() + candidate.width();
                    boolean yOverlap = y < r.y + r.height && r.y < y + candidate.height();
                    if (xOverlap && yOverlap) {
                        nextY = Math.max(nextY, r.y + r.height + LABEL_GAP);
                    }
                }
                if (nextY == y) {
                    break;
                }
                y = nextY;
            }
            placed.add(new TranslationLabel(candidate.item(),
                    new Rectangle(candidate.x(), y, candidate.width(), candidate.height()),
                    candidate.fontSize(), candidate.lines()));
        }
        placed.sort(Comparator.comparingInt(label -> label.rect().y));
        return placed;
    }

    private void drawTranslationLabel(Graphics2D g, TranslationLabel label, Font badgeFont) {
        Rectangle rect = label.rect();
        int index = label.item().index();
        Color color = colorFor(index);
        String badge = "#" + index;

        g.setColor(lighten(color, 0.88));
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        g.setColor(color);
        g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);

        Font textFont = new Font(fontFamily, Font.PLAIN, label.fontSize());
        g.setFont(textFont);
        FontMetrics textMetrics = g.getFontMetrics();
        g.setColor(TEXT_COLOR);
        int textX = rect.x + LABEL_PAD_X;
        int baseline = rect.y + LABEL_PAD_Y + textMetrics.getAscent();
        for (int i = 0; i < label.lines().size(); i++) {
            g.drawString(label.lines().get(i), textX, baseline + i * textMetrics.getHeight());
        }

        g.setFont(badgeFont);
        FontMetrics badgeMetrics = g.getFontMetrics();
        int badgeWidth = badgeMetrics.stringWidth(badge) + 8;
        int badgeHeight = badgeMetrics.getHeight();
        int bx = rect.x + rect.width - badgeWidth;
        int by = rect.y + rect.height - badgeHeight;
        drawBadge(g, badge, bx, by, badgeWidth, badgeHeight, badgeMetrics, withAlpha(color, BADGE_ALPHA));
    }

    // ------------------------------------------------------------------
    // 公共绘图工具
    // ------------------------------------------------------------------

    /** 画整个面板：白底 + 灰外框 + 顶部 header（标题 + 可选副条带，均为浅灰）+ 标题文字。 */
    private void drawPanelChrome(Graphics2D g, int panelX, int panelY, int panelWidth, int panelHeight,
                                 int headerHeight, String title, Font titleFont) {
        g.setColor(Color.WHITE);
        g.fillRect(panelX, panelY, panelWidth, panelHeight);
        g.setColor(PANEL_BORDER);
        g.drawRect(panelX, panelY, panelWidth - 1, panelHeight - 1);

        g.setColor(TITLE_BG);
        g.fillRect(panelX + 1, panelY + 1, panelWidth - 2, headerHeight - 1);
        if (headerHeight > TITLE_HEIGHT) {
            g.setColor(PANEL_BORDER);
            g.drawLine(panelX + 1, panelY + TITLE_HEIGHT, panelX + panelWidth - 2, panelY + TITLE_HEIGHT);
        }
        g.setColor(ACCENT);
        g.setFont(titleFont);
        g.drawString(title, panelX + TITLE_PAD_X, panelY + 29);
    }

    private void drawBadge(Graphics2D g, String text, int x, int y, int width, int height,
                           FontMetrics metrics, Color fill) {
        g.setColor(fill);
        g.fillRect(x, y, width, height);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 4, y + metrics.getAscent());
    }

    private void drawEmptyHint(Graphics2D g, int imageX, int imageY, int imageWidth, int imageHeight, Font font) {
        g.setFont(font);
        String text = "未识别到可翻译文字";
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 28;
        int height = metrics.getHeight() + 18;
        int x = imageX + (imageWidth - width) / 2;
        int y = imageY + (imageHeight - height) / 2;

        g.setColor(new Color(17, 24, 39, 170));
        g.fillRect(x, y, width, height);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 14, y + 12 + metrics.getAscent());
    }

    private BufferedImage scaleImage(BufferedImage sourceImage, double scale) {
        int width = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
        if (width == sourceImage.getWidth() && height == sourceImage.getHeight()) {
            return sourceImage;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            applyQualityHints(g);
            g.drawImage(sourceImage, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private Box scaledBox(OcrTextItem item, int imageX, int imageY, double scale) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point2D.Double point : item.bbox()) {
            int x = imageX + (int) Math.round(point.x * scale);
            int y = imageY + (int) Math.round(point.y * scale);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new Box(minX, minY, maxX, maxY);
    }

    private int fontSizeForBox(int boxHeight) {
        int size = (int) Math.round(boxHeight * 0.6);
        return clamp(size, MIN_FONT_SIZE, MAX_FONT_SIZE);
    }

    private List<String> wrapText(String text, FontMetrics metrics, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        boolean truncated = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\r') {
                continue;
            }
            if (ch == '\n') {
                lines.add(line.toString());
                line.setLength(0);
            } else {
                String candidate = line + String.valueOf(ch);
                if (!line.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(ch);
                } else {
                    line.append(ch);
                }
            }
            if (lines.size() == maxLines) {
                truncated = i < text.length() - 1 || !line.isEmpty();
                break;
            }
        }

        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        if (truncated) {
            int lastIndex = lines.size() - 1;
            lines.set(lastIndex, fitWithEllipsis(lines.get(lastIndex), metrics, maxWidth));
        }
        return lines;
    }

    private String fitWithEllipsis(String text, FontMetrics metrics, int maxWidth) {
        String suffix = "…";
        String value = text;
        while (!value.isEmpty() && metrics.stringWidth(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private String resolveFontFamily() {
        Set<String> families = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ));
        List<String> candidates = List.of(
                "Microsoft YaHei",
                "SimHei",
                "Noto Sans CJK SC",
                "Source Han Sans SC",
                "WenQuanYi Micro Hei",
                "Arial Unicode MS",
                Font.SANS_SERIF
        );
        return candidates.stream()
                .filter(families::contains)
                .findFirst()
                .orElse(Font.SANS_SERIF);
    }

    private Color colorFor(int index) {
        int i = ((index - 1) % PALETTE.length + PALETTE.length) % PALETTE.length;
        return PALETTE[i];
    }

    private Color lighten(Color color, double whiteRatio) {
        double keep = 1.0 - whiteRatio;
        int r = (int) Math.round(color.getRed() * keep + 255 * whiteRatio);
        int g = (int) Math.round(color.getGreen() * keep + 255 * whiteRatio);
        int b = (int) Math.round(color.getBlue() * keep + 255 * whiteRatio);
        return new Color(clamp(r, 0, 255), clamp(g, 0, 255), clamp(b, 0, 255));
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha, 0, 255));
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private record Box(int minX, int minY, int maxX, int maxY) {
        int width() {
            return maxX - minX;
        }

        int height() {
            return maxY - minY;
        }
    }

    private record LayoutCandidate(OcrTextItem item, int x, int width, int height, int desiredY,
                                   int fontSize, List<String> lines) {
    }

    private record TranslationLabel(OcrTextItem item, Rectangle rect, int fontSize, List<String> lines) {
    }
}
