package com.hollow.build;

import com.hollow.build.ocr.service.OcrTranslationImageRenderer;
import com.hollow.build.ocr.service.OcrTranslationImageRenderer.OcrTextItem;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 渲染器可视化验证：合成网页风格 / 侧栏风格的原图，喂给 {@link OcrTranslationImageRenderer}
 * 输出对照图到 {@code target/} 下，用于人工查看右栏译文位置对齐、配色对应、元信息条等效果。
 * 非 Spring 测试，单独运行：
 * <pre>$env:JAVA_HOME="..."; mvn -q test -Dtest=OcrTranslationImageRendererVisualTest -DfailIfNoTests=false</pre>
 */
class OcrTranslationImageRendererVisualTest {

    private record Spec(String en, String zh, int x, int y, int fontSize) {
    }

    /**
     * 宽图场景：网页式截图，含宽标题行 / 左右并列窄列 / 底部密集多行。
     * 验证：左右列各自独立保持位置、底部不堆叠、元信息单行右对齐于标题栏。
     */
    @Test
    void renderPreview() throws Exception {
        int width = 920;
        int height = 470;

        List<Spec> specs = List.of(
                new Spec("Thanks to AICodeMirror for sponsoring this project",
                        "感谢 AICodeMirror 赞助本项目", 30, 28, 24),
                new Spec("Get free Claude Code credits and higher rate limits for AI coding.",
                        "获取免费的 Claude Code 额度,以及更高的 AI 编码速率上限。", 30, 78, 17),

                new Spec("Features", "功能特性", 30, 150, 17),
                new Spec("Installation", "安装方式", 30, 188, 17),
                new Spec("Quick Start Guide", "快速开始指南", 30, 226, 17),

                new Spec("Pricing Plans", "定价方案", 470, 150, 17),
                new Spec("Community", "社区", 470, 188, 17),
                new Spec("Changelog", "更新日志", 470, 226, 17),

                new Spec("1. Install the CLI via npm or Homebrew package manager",
                        "1. 通过 npm 或 Homebrew 包管理器安装 CLI", 30, 300, 15),
                new Spec("2. Run the login command to authenticate your account",
                        "2. 运行登录命令以验证你的账户", 30, 330, 15),
                new Spec("3. Point it at your repository and start coding with AI",
                        "3. 将其指向你的仓库并开始用 AI 编码", 30, 360, 15),
                new Spec("4. Join our Discord server for help, tips and updates",
                        "4. 加入我们的 Discord 服务器获取帮助、技巧与更新", 30, 390, 15),
                new Spec("5. Read the full documentation at example.com/docs",
                        "5. 在 example.com/docs 阅读完整文档", 30, 420, 15)
        );

        renderAndWrite(width, height, specs, "ocr-translation-preview.png",
                new OcrTranslationImageRenderer.RenderStats(327, 1234));
    }

    /**
     * 窄图场景：侧栏导航式截图（窄宽 + 多个垂直堆叠的短文本 + 底部一个较长的描述）。
     * 验证：(1) 元信息单行放不下时自动换到标题下方独立副条带；
     *      (2) 当右栏内容比原图高时，左栏 panel 边框跟随对齐到底部，不出现"漂浮"缺边。
     */
    @Test
    void renderNarrowPreview() throws Exception {
        int width = 360;
        int height = 400;

        List<Spec> specs = List.of(
                new Spec("MinIO Object Store", "MinIO 对象存储", 16, 14, 18),
                new Spec("User", "用户", 16, 60, 14),
                new Spec("Object Browser", "对象浏览器", 16, 92, 14),
                new Spec("Access Key", "访问密钥", 16, 124, 14),
                new Spec("Documentation", "文档", 16, 156, 14),
                new Spec("Administrator", "管理员", 16, 188, 14),
                new Spec("Bucket Management", "存储桶管理", 16, 220, 14),
                new Spec("Policy", "策略", 16, 252, 14),
                new Spec("Identity & Access", "身份与访问", 16, 284, 14),
                new Spec("Monitoring", "监控", 16, 316, 14),
                new Spec("Recent Event Log Entries", "近期事件日志条目(按时间倒序排列)", 16, 348, 14)
        );

        renderAndWrite(width, height, specs, "ocr-translation-narrow-preview.png",
                new OcrTranslationImageRenderer.RenderStats(742, 7531));
    }

    private void renderAndWrite(int width, int height, List<Spec> specs, String outputName,
                                OcrTranslationImageRenderer.RenderStats stats) throws Exception {
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        List<OcrTextItem> items = drawSourceAndCollect(source, specs);

        byte[] png = new OcrTranslationImageRenderer().render(source, items, "中文", stats);

        Path out = Path.of("target", outputName);
        Files.createDirectories(out.getParent());
        Files.write(out, png);
        System.out.println("preview written: " + out.toAbsolutePath() + " (" + png.length + " bytes)");
    }

    /** 在原图上画 spec 文字，同时记录每段文字的 bbox 作为 OCR 检测结果。 */
    private List<OcrTextItem> drawSourceAndCollect(BufferedImage source, List<Spec> specs) {
        Graphics2D g = source.createGraphics();
        List<OcrTextItem> items = new ArrayList<>();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.setColor(new Color(37, 99, 235));
            g.fillRect(0, 0, source.getWidth(), 6);

            int index = 1;
            for (Spec spec : specs) {
                Font font = new Font("Microsoft YaHei", Font.PLAIN, spec.fontSize());
                g.setFont(font);
                FontMetrics fm = g.getFontMetrics();
                g.setColor(new Color(31, 41, 51));
                g.drawString(spec.en(), spec.x(), spec.y() + fm.getAscent());

                int left = spec.x() - 3;
                int top = spec.y() - 2;
                int right = spec.x() + fm.stringWidth(spec.en()) + 3;
                int bottom = spec.y() + fm.getHeight();
                List<Point2D.Double> bbox = List.of(
                        new Point2D.Double(left, top),
                        new Point2D.Double(right, top),
                        new Point2D.Double(right, bottom),
                        new Point2D.Double(left, bottom)
                );
                items.add(new OcrTextItem(index++, spec.en(), spec.zh(), bbox));
            }
        } finally {
            g.dispose();
        }
        return items;
    }
}
